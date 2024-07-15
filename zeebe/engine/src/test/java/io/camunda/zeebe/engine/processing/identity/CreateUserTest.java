package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CreateUserTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateUser() {
    // given
    final var createdUser =
        ENGINE.user().newUser("foo@bar.com").withName("Foo Bar").withEmail("foo@bar.com").create();

    System.out.println("foo");
  }

  @Test
  public void shouldNotDuplicate() {
    // given
    final var createdUser =
        ENGINE.user().newUser("foo@bar.com").withName("Foo Bar").withEmail("foo@bar.com").create();

    // when
    final var duplicatedUser =
        ENGINE.user().newUser("foo@bar.com").withName("Bar Foo").withEmail("bar@foo.com").create();

    System.out.println("foo");
  }
}
