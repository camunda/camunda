/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;
import org.camunda.optimize.service.identity.CCSMIdentityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CCSMAlertRecipientValidatorTest {
  private static final String TEST_EMAIL_1 = "test1@test.com";
  private static final String TEST_EMAIL_2 = "test2@test.com";
  private static final UserDto TEST_USER_1 = new UserDto("id1", "name1", "name1", TEST_EMAIL_1);
  private static final UserDto TEST_USER_2 = new UserDto("id2", "name2", "name2", TEST_EMAIL_2);

  @Mock private CCSMIdentityService identityService;

  @InjectMocks private CCSMAlertRecipientValidator ccsmAlertRecipientValidator;

  @Test
  public void alertValidationSucceedsWhenAllEmailsKnown() {
    // given
    final Set<String> emailsToValidate = Set.of(TEST_EMAIL_1, TEST_EMAIL_2);
    when(identityService.getUsersByEmail(emailsToValidate))
        .thenReturn(List.of(TEST_USER_1, TEST_USER_2));

    // when/then
    assertDoesNotThrow(
        () ->
            ccsmAlertRecipientValidator.validateAlertRecipientEmailAddresses(
                emailsToValidate.stream().toList()));
  }

  @Test
  public void alertValidationNoErrorsWhenEmpty() {
    // given
    when(identityService.getUsersByEmail(Collections.emptySet()))
        .thenReturn(Collections.emptyList());

    // when/then
    assertDoesNotThrow(
        () ->
            ccsmAlertRecipientValidator.validateAlertRecipientEmailAddresses(
                Collections.emptyList()));
  }

  @Test
  public void alertValidationFailsForUnknownEmails() {
    // given
    final Set<String> emailsToValidate = Set.of(TEST_EMAIL_1, TEST_EMAIL_2);
    when(identityService.getUsersByEmail(emailsToValidate)).thenReturn(List.of(TEST_USER_1));

    // when/then
    final OptimizeAlertEmailValidationException thrown =
        assertThrows(
            OptimizeAlertEmailValidationException.class,
            () ->
                ccsmAlertRecipientValidator.validateAlertRecipientEmailAddresses(
                    emailsToValidate.stream().toList()));
    assertThat(thrown.getAlertEmails()).containsExactly(TEST_EMAIL_2);
  }
}
