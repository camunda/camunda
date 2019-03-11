# Writing Tests

You can use the `zeebe-test` module to write JUnit tests for your job worker and BPMN workflow. It provides a JUnit rule to bootstrap the broker and some basic assertions.

## Usage in a Maven project

Add `zeebe-test` as Maven test dependency to your project:

```xml
<dependency>
  <groupId>io.zeebe</groupId>
  <artifactId>zeebe-test</artifactId>
  <scope>test</scope>
</dependency>
```

## Bootstrap the Broker

Use the `ZeebeTestRule` in your test case to start an embedded broker. It contains a client which can be used to deploy a BPMN workflow or create an instance.

```java
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MyTest {

  @Rule public final ZeebeTestRule testRule = new ZeebeTestRule();

  private ZeebeClient client;

  @Test
  public void test() {
  	client = testRule.getClient();

    client
        .newDeployCommand()
        .addResourceFromClasspath("process.bpmn")
        .send()
        .join();  	
  
    final WorkflowInstanceEvent workflowInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();
  }
}
```

## Verify the Result

The `ZeebeTestRule` provides also some basic assertions in AssertJ style. The entry point of the assertions is `ZeebeTestRule.assertThat(...)`. 

```java
final WorkflowInstanceEvent workflowInstance = ...

ZeebeTestRule.assertThat(workflowInstance)
    .isEnded()
    .hasPassed("start", "task", "end")
    .hasVariables("result", 21.0);
```

