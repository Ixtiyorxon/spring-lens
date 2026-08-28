package com.sdlcpro.springlens.exposure.application;

import com.sdlcpro.springlens.model.application.ApplicationInfo;
import com.sdlcpro.springlens.model.application.JavaInfo;
import com.sdlcpro.springlens.model.application.SpringInfo;
import com.sdlcpro.springlens.model.application.StartupInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web integration tests for {@link ApplicationInfoRestController}.
 *
 * @author Ixtiyorxon
 * @since 2026-08-28
 */
@DisplayName("ApplicationInfoRestController Web Integration Tests")
class ApplicationInfoRestControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var applicationInfo = new ApplicationInfo(
                "orders-service",
                Set.of("prod"),
                Set.of("default"),
                new SpringInfo("3.2.0", "6.1.0"),
                new JavaInfo("17", "Eclipse Adoptium"),
                new StartupInfo(Instant.parse("2026-08-28T10:00:00Z"), Duration.ofMillis(450))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(
                new ApplicationInfoRestController(() -> applicationInfo)
        ).build();
    }

    @Test
    @DisplayName("Returns application metadata as JSON")
    void returnsApplicationMetadataAsJson() throws Exception {
        mockMvc.perform(get("/spring-lens/api/application").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("orders-service"))
                .andExpect(jsonPath("$.activeProfiles[0]").value("prod"))
                .andExpect(jsonPath("$.defaultProfiles[0]").value("default"))
                .andExpect(jsonPath("$.spring.bootVersion").value("3.2.0"))
                .andExpect(jsonPath("$.spring.frameworkVersion").value("6.1.0"))
                .andExpect(jsonPath("$.java.version").value("17"))
                .andExpect(jsonPath("$.java.vendor").value("Eclipse Adoptium"));
    }

    @Test
    @DisplayName("Returns the configured fallback message when metadata is unavailable")
    void returnsFallbackMessageWhenMetadataIsUnavailable() throws Exception {
        var unavailableMockMvc = MockMvcBuilders.standaloneSetup(
                new ApplicationInfoRestController(() -> null)
        ).build();

        unavailableMockMvc.perform(get("/spring-lens/api/application").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ApplicationInfo not found!"));
    }
}
