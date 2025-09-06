package io.camunda.zeebe.gateway.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.gateway.validation.model.ValidationErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

/**
 * Verifies that a ServiceException carrying a validation style message (CODE:{json})
 * is mapped to a ProblemDetail with structured properties.
 */
public class ValidationErrorMappingTest {

  @Test
  void shouldParseValidationPayloadIntoProblemDetail() {
    final String payloadJson = "{\"groupId\":\"X\",\"code\":\"NO_MATCH\",\"summary\":\"s\"}";
    final ServiceException se = new ServiceException(ValidationErrorCode.NO_MATCH.name() + ":" + payloadJson, Status.INVALID_ARGUMENT);
    final ProblemDetail pd = RestErrorMapper.mapErrorToProblem(se);
    assertThat(pd.getStatus()).isNotNull();
    assertThat(pd.getProperties())
        .containsEntry("groupId", "X")
        .containsEntry("code", "NO_MATCH")
        .containsEntry("validationCode", ValidationErrorCode.NO_MATCH.name());
    assertThat(pd.getDetail()).isEqualTo("s");
  }
}
