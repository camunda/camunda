package io.camunda.zeebe.engine.state;

import java.util.Optional;
import org.agrona.DirectBuffer;

public interface QueryService {

  Optional<DirectBuffer> getBpmnProcessIdForProcess(Long key);

  Optional<DirectBuffer> getBpmnProcessIdForProcessInstance(Long key);

  Optional<DirectBuffer> getBpmnProcessIdForJob(Long key);
}
