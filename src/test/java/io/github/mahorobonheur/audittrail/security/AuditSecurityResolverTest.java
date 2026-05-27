package io.github.mahorobonheur.audittrail.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSecurityResolverTest {

    private final AuditSecurityResolver resolver = new AuditSecurityResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Returns anonymous when SecurityContext has no authentication")
    void noAuthentication_returnsAnonymous() {
        SecurityContextHolder.clearContext();

        assertThat(resolver.getCurrentUser()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("Returns anonymous when authentication is not authenticated")
    void notAuthenticated_returnsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("guest", "n/a"));

        assertThat(resolver.getCurrentUser()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("Returns principal name when user is authenticated")
    void authenticated_returnsPrincipalName() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "bonheur", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThat(resolver.getCurrentUser()).isEqualTo("bonheur");
    }

    @Test
    @DisplayName("Returns anonymous when principal name is blank")
    void blankPrincipalName_returnsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("   ", "n/a"));

        assertThat(resolver.getCurrentUser()).isEqualTo("anonymous");
    }
}
