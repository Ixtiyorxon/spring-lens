package com.sdlcpro.springlens.insight.bean.condition;

import com.sdlcpro.springlens.annotation.SpringLensInternalComponent;
import com.sdlcpro.springlens.insight.support.matcher.PackageMatcher;
import com.sdlcpro.springlens.insight.util.SafeListenerInvoker;
import com.sdlcpro.springlens.listener.bean.ConditionEvaluationInfoCollectListener;
import com.sdlcpro.springlens.matcher.CompositeMatcher;
import com.sdlcpro.springlens.model.bean.condition.ConditionEvaluationInfo;
import com.sdlcpro.springlens.model.bean.condition.ConditionMatch;
import com.sdlcpro.springlens.model.bean.condition.ConditionOutcome;
import com.sdlcpro.springlens.util.ClassInspector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SpringLensInternalComponent
public class ConditionEvaluationInfoCollector implements SmartInitializingSingleton {
    private static final String SPRING_LENS_BASE_PACKAGE = "com.sdlcpro.springlens.**";

    private final ApplicationContext context;
    private final ObjectProvider<ConditionEvaluationInfoCollectListener> conditionEvaluationInfoCollectListenerProvider;
    private final CompositeMatcher<ConditionEvaluationCollectionContext> conditionEvaluationCollectionMatcher;

    public ConditionEvaluationInfoCollector(
            ApplicationContext context, ConditionReportSettings settings,
            ObjectProvider<ConditionEvaluationInfoCollectListener> conditionEvaluationInfoCollectListenerProvider) {
        this.context = context;
        this.conditionEvaluationInfoCollectListenerProvider = conditionEvaluationInfoCollectListenerProvider;
        this.conditionEvaluationCollectionMatcher = createCollectionMatcher(settings);
    }

    private CompositeMatcher<ConditionEvaluationCollectionContext> createCollectionMatcher(
            ConditionReportSettings settings) {
        var matcher = new CompositeMatcher<ConditionEvaluationCollectionContext>();
        matcher.addExcludeMatcher(new PackageMatcher<>(settings.excludePackagePatterns()));
        if (!settings.includeToolInternal()) {
            matcher.addExcludeMatcher(new PackageMatcher<>(Set.of(SPRING_LENS_BASE_PACKAGE)));
        }

        return matcher;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<ConditionEvaluationInfo> conditionEvaluationInfos = this.collectConditionEvaluationInfo();
        this.publishConditionEvaluationInfo(conditionEvaluationInfos);
    }

    public List<ConditionEvaluationInfo> collectConditionEvaluationInfo() {
        var conditionEvaluationInfos = new LinkedList<ConditionEvaluationInfo>();
        this.collectConditionEvaluationInfoRecursively(this.context, conditionEvaluationInfos);
        return conditionEvaluationInfos;
    }

    private void collectConditionEvaluationInfoRecursively(
            ApplicationContext context,
            List<ConditionEvaluationInfo> conditionEvaluationInfos) {

        var parentContext = context.getParent();
        if (parentContext != null) {
            this.collectConditionEvaluationInfoRecursively(parentContext, conditionEvaluationInfos);
        }

        if (!(context instanceof ConfigurableApplicationContext configurableApplicationContext)) {
            return;
        }

        var conditionEvaluationReport = ConditionEvaluationReport.get(configurableApplicationContext.getBeanFactory());

        String contextId = context.getId() == null ? ObjectUtils.identityToString(context) : context.getId();
        var outcomeMap = conditionEvaluationReport.getConditionAndOutcomesBySource();
        for (Map.Entry<String, ConditionEvaluationReport.ConditionAndOutcomes> entry : outcomeMap.entrySet()) {
            String source = entry.getKey();
            ConditionEvaluationReport.ConditionAndOutcomes conditionAndOutcomes = entry.getValue();
            if (this.isEligibleToCollectInfo(source)) {
                var conditionEvaluationInfo = createConditionEvaluationInfo(contextId, source, conditionAndOutcomes);
                conditionEvaluationInfos.add(conditionEvaluationInfo);
            }
        }
    }

    public boolean isEligibleToCollectInfo(String source) {
        var context = new ConditionEvaluationCollectionContext(source);
        return this.conditionEvaluationCollectionMatcher.matches(context);
    }

    private ConditionEvaluationInfo createConditionEvaluationInfo(
            String contextId, String source,
            ConditionEvaluationReport.ConditionAndOutcomes conditionAndOutcomes) {

        var matches = new LinkedList<ConditionMatch>();
        for (ConditionEvaluationReport.ConditionAndOutcome outcome : conditionAndOutcomes) {
            var condition = ClassInspector.getClassName(outcome.getCondition().getClass());
            boolean matched = outcome.getOutcome().isMatch();
            var message = outcome.getOutcome().getMessage();
            if (!StringUtils.hasLength(message)) {
                message = matched ? "matched" : "did not match";
            }
            matches.add(new ConditionMatch(condition, matched, message));
        }

        return new ConditionEvaluationInfo(
                contextId,
                source,
                conditionAndOutcomes.isFullMatch() ? ConditionOutcome.MATCHED : ConditionOutcome.NOT_MATCHED,
                matches
        );
    }

    private void publishConditionEvaluationInfo(List<ConditionEvaluationInfo> conditionEvaluationInfos) {
        for (var conditionEvaluationInfo : conditionEvaluationInfos) {
            SafeListenerInvoker.invoke(
                    this.conditionEvaluationInfoCollectListenerProvider,
                    conditionEvaluationInfo,
                    ConditionEvaluationInfoCollectListener::onConditionEvaluationInfoCollect
            );
        }
    }
}
