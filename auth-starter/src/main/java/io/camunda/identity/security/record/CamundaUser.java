package io.camunda.identity.security.record;

import java.util.List;

public record CamundaUser(
    String username,
    List<String> Roles
) {
}
