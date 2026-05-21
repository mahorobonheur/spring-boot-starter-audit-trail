package io.github.mahorobonheur.audittrail.engine;

import io.github.mahorobonheur.audittrail.annotation.AuditExclude;
import io.github.mahorobonheur.audittrail.annotation.AuditTrail;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FieldDiffEngine}.
 *
 * @author Bonheur Mahoro
 */
class FieldDiffEngineTest {

    private FieldDiffEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FieldDiffEngine();
    }

    // ── Test entities ────────────────────────────────────────────────────────

    @AuditTrail
    static class SimpleEntity {
        String name;
        String email;
        int age;

        SimpleEntity(String name, String email, int age) {
            this.name  = name;
            this.email = email;
            this.age   = age;
        }
    }

    @AuditTrail(exclude = {"password"})
    static class EntityWithExclusion {
        String username;
        String password;
        String role;

        EntityWithExclusion(String username, String password, String role) {
            this.username = username;
            this.password = password;
            this.role     = role;
        }
    }

    @AuditTrail
    static class EntityWithFieldAnnotation {
        String email;

        @AuditExclude
        String token;

        EntityWithFieldAnnotation(String email, String token) {
            this.email = email;
            this.token = token;
        }
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns empty list when no fields have changed")
    void noChanges_returnsEmptyList() {
        SimpleEntity oldE = new SimpleEntity("Alice", "alice@x.com", 30);
        SimpleEntity newE = new SimpleEntity("Alice", "alice@x.com", 30);

        List<FieldDiff> diffs = engine.diff(oldE, newE);

        assertThat(diffs).isEmpty();
    }

    @Test
    @DisplayName("Detects a single changed field")
    void singleFieldChanged_returnsDiff() {
        SimpleEntity oldE = new SimpleEntity("Alice", "alice@x.com", 30);
        SimpleEntity newE = new SimpleEntity("Alice", "newalice@x.com", 30);

        List<FieldDiff> diffs = engine.diff(oldE, newE);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).field()).isEqualTo("email");
        assertThat(diffs.get(0).oldValue()).isEqualTo("alice@x.com");
        assertThat(diffs.get(0).newValue()).isEqualTo("newalice@x.com");
    }

    @Test
    @DisplayName("Detects multiple changed fields")
    void multipleFieldsChanged_returnsAllDiffs() {
        SimpleEntity oldE = new SimpleEntity("Alice", "alice@x.com", 30);
        SimpleEntity newE = new SimpleEntity("Bob",   "bob@x.com",   25);

        List<FieldDiff> diffs = engine.diff(oldE, newE);

        assertThat(diffs).hasSize(3);
        assertThat(diffs).extracting(FieldDiff::field)
                .containsExactlyInAnyOrder("name", "email", "age");
    }

    @Test
    @DisplayName("On CREATE (null old entity) all fields appear with null oldValue")
    void createEvent_oldEntityNull_allFieldsWithNullOldValue() {
        SimpleEntity newE = new SimpleEntity("Alice", "alice@x.com", 30);

        List<FieldDiff> diffs = engine.diff(null, newE);

        assertThat(diffs).isNotEmpty();
        assertThat(diffs).allSatisfy(d -> assertThat(d.oldValue()).isNull());
        assertThat(diffs).extracting(FieldDiff::field)
                .contains("name", "email", "age");
    }

    @Test
    @DisplayName("On DELETE (null new entity) all fields appear with null newValue")
    void deleteEvent_newEntityNull_allFieldsWithNullNewValue() {
        SimpleEntity oldE = new SimpleEntity("Alice", "alice@x.com", 30);

        List<FieldDiff> diffs = engine.diff(oldE, null);

        assertThat(diffs).isNotEmpty();
        assertThat(diffs).allSatisfy(d -> assertThat(d.newValue()).isNull());
    }

    @Test
    @DisplayName("Fields in @AuditTrail(exclude) are not included in diffs")
    void excludedByAnnotationAttribute_notInDiff() {
        EntityWithExclusion oldE = new EntityWithExclusion("alice", "secret123", "USER");
        EntityWithExclusion newE = new EntityWithExclusion("alice", "newpassword", "ADMIN");

        List<FieldDiff> diffs = engine.diff(oldE, newE);

        assertThat(diffs).extracting(FieldDiff::field).doesNotContain("password");
        assertThat(diffs).extracting(FieldDiff::field).contains("role");
    }

    @Test
    @DisplayName("Fields annotated with @AuditExclude are not included in diffs")
    void excludedByFieldAnnotation_notInDiff() {
        EntityWithFieldAnnotation oldE = new EntityWithFieldAnnotation("alice@x.com", "token-old");
        EntityWithFieldAnnotation newE = new EntityWithFieldAnnotation("newalice@x.com", "token-new");

        List<FieldDiff> diffs = engine.diff(oldE, newE);

        assertThat(diffs).extracting(FieldDiff::field).doesNotContain("token");
        assertThat(diffs).extracting(FieldDiff::field).contains("email");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when both entities are null")
    void bothNull_throwsException() {
        assertThatThrownBy(() -> engine.diff(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-null");
    }

    @Test
    @DisplayName("FieldDiff toString produces readable output")
    void fieldDiffToString_isReadable() {
        FieldDiff diff = new FieldDiff("email", "old@x.com", "new@x.com");
        assertThat(diff.toString()).contains("email", "old@x.com", "new@x.com");
    }
}
