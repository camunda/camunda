# Zeebe Test

JUnit test rules for Zeebe applications.

## Usage example

Add `zeebe-test` as test dependency to your project.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.zeebe</groupId>
      <artifactId>zeebe-bom</artifactId>
      <version>${ZEEBE_VERSION}</version>
      <scope>import</scope>
      <type>pom</type>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  
  <dependency>
    <groupId>io.zeebe</groupId>
    <artifactId>zeebe-client-java</artifactId>
  </dependency>

  <dependency>
    <groupId>io.zeebe</groupId>
    <artifactId>zeebe-test</artifactId>
    <scope>test</scope>
  </dependency>
  
</dependencies>
```

Use the `ZeebeTestRule` in your test case to start an embedded broker and client.

```java
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WorkflowTest {
  @Rule public final ZeebeTestRule testRule = new ZeebeTestRule();

  private ZeebeClient client;

  @Before
  public void deploy() {
    client = testRule.getClient();

    client
        .workflowClient()
        .newDeployCommand()
        .addResourceFromClasspath("process.bpmn")
        .send()
        .join();
  }

  @Test
  public void shouldCompleteWorkflowInstance() {
    final WorkflowInstanceEvent workflowInstance =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    client
        .jobClient()
        .newWorker()
        .jobType("task")
        .handler((c, j) -> c.newCompleteCommand(j.getKey()).send().join())
        .name("test")
        .open();

    ZeebeTestRule.assertThat(workflowInstance)
        .isEnded()
        .hasPassed("start", "task", "end");
  }
}
```
