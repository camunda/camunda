# Zeebe Test

JUnit test rules for Zeebe applications.

## 🚨 Deprecation Warning 🚨

This module is deprecated and will be removed in a future release.
See [8143](https://github.com/camunda-cloud/zeebe/issues/8143) for more information.

## Usage example

Add `zeebe-test` as test dependency to your project.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.camunda</groupId>
      <artifactId>zeebe-bom</artifactId>
      <version>${ZEEBE_VERSION}</version>
      <scope>import</scope>
      <type>pom</type>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>

  <dependency>
    <groupId>io.camunda</groupId>
    <artifactId>zeebe-client-java</artifactId>
  </dependency>

  <dependency>
    <groupId>io.camunda</groupId>
    <artifactId>zeebe-test</artifactId>
    <scope>test</scope>
  </dependency>

</dependencies>
```

Use the `ZeebeTestRule` in your test case to start an embedded broker and client.

```java
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ProcessTest {
  @Rule public final ZeebeTestRule testRule = new ZeebeTestRule();

  private ZeebeClient client;

  @Before
  public void deploy() {
    client = testRule.getClient();

    client
        .newDeployCommand()
        .addResourceFromClasspath("process.bpmn")
        .send()
        .join();
  }

  @Test
  public void shouldCompleteProcessInstance() {
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    client
        .newWorker()
        .jobType("task")
        .handler((c, j) -> c.newCompleteCommand(j.getKey()).send().join())
        .name("test")
        .open();

    ZeebeTestRule.assertThat(processInstance)
        .isEnded()
        .hasPassed("start", "task", "end");
  }
}
```
