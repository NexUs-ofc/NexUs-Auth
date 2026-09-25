package com.example.nexusauth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GoogleAuthConfigTest {

    @Test
    void parsesMultipleClientIds() {
        assertThat(GoogleAuthConfig.audiences("web.apps.googleusercontent.com,android.apps.googleusercontent.com"))
                .containsExactly("web.apps.googleusercontent.com", "android.apps.googleusercontent.com");
    }

    @Test
    void ignoresBlankAndDuplicatedClientIds() {
        assertThat(GoogleAuthConfig.audiences(" web.apps.googleusercontent.com , ,web.apps.googleusercontent.com,"))
                .containsExactly("web.apps.googleusercontent.com");
    }

    @Test
    void returnsEmptyAudienceWhenNotConfigured() {
        assertThat(GoogleAuthConfig.audiences(null)).isEmpty();
        assertThat(GoogleAuthConfig.audiences("  ")).isEmpty();
    }

    @Test
    void buildsNoVerifierWhenAudienceIsEmpty() {
        assertThat(new GoogleAuthConfig().googleIdTokenVerifier("")).isNull();
    }
}
