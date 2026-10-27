/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.command.MalformedResponseException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ResolveSecretsResponse;
import io.camunda.client.api.response.ResolveSecretsResponse.ResolutionError;
import io.camunda.client.api.search.enums.SecretErrorCode;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.ResolvedSecret;
import io.camunda.client.protocol.rest.SecretResolutionError;
import io.camunda.client.protocol.rest.SecretResolveRequest;
import io.camunda.client.protocol.rest.SecretResolveResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class ResolveSecretsTest extends ClientRestTest {

  private static final String REFERENCE = "camunda.secrets.token";
  private static final String OTHER_REFERENCE = "camunda.secrets.a";
  private static final String VALUE = "s3cr3t-value";

  @Test
  void shouldPostToResolveEndpoint() {
    // given
    gatewayService.onSecretsResolveRequest(new SecretResolveResult());

    // when
    client.newResolveSecretsCommand().reference(REFERENCE).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getSecretsResolveUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldSendAllReferencesInRequestBody() {
    // given
    gatewayService.onSecretsResolveRequest(new SecretResolveResult());

    // when
    client
        .newResolveSecretsCommand()
        .references(Arrays.asList(REFERENCE, OTHER_REFERENCE))
        .send()
        .join();

    // then
    final SecretResolveRequest request = gatewayService.getLastRequest(SecretResolveRequest.class);
    assertThat(request.getReferences()).containsExactly(REFERENCE, OTHER_REFERENCE);
  }

  @Test
  void shouldSendReferencesGivenAsVarargs() {
    // given
    gatewayService.onSecretsResolveRequest(new SecretResolveResult());

    // when
    client.newResolveSecretsCommand().references(REFERENCE, OTHER_REFERENCE).send().join();

    // then
    final SecretResolveRequest request = gatewayService.getLastRequest(SecretResolveRequest.class);
    assertThat(request.getReferences()).containsExactly(REFERENCE, OTHER_REFERENCE);
  }

  @Test
  void shouldAppendSingleReferences() {
    // given
    gatewayService.onSecretsResolveRequest(new SecretResolveResult());

    // when
    client.newResolveSecretsCommand().reference(REFERENCE).reference(OTHER_REFERENCE).send().join();

    // then
    final SecretResolveRequest request = gatewayService.getLastRequest(SecretResolveRequest.class);
    assertThat(request.getReferences()).containsExactly(REFERENCE, OTHER_REFERENCE);
  }

  @Test
  void shouldReplacePreviouslySetReferences() {
    // given
    gatewayService.onSecretsResolveRequest(new SecretResolveResult());

    // when
    client
        .newResolveSecretsCommand()
        .references(Collections.singletonList(REFERENCE))
        .references(Collections.singletonList(OTHER_REFERENCE))
        .send()
        .join();

    // then
    final SecretResolveRequest request = gatewayService.getLastRequest(SecretResolveRequest.class);
    assertThat(request.getReferences()).containsExactly(OTHER_REFERENCE);
  }

  @Test
  void shouldSendDuplicateReferencesUnchanged() {
    // given deduplication is owned by the cluster
    gatewayService.onSecretsResolveRequest(new SecretResolveResult());

    // when
    client.newResolveSecretsCommand().references(REFERENCE, REFERENCE).send().join();

    // then
    final SecretResolveRequest request = gatewayService.getLastRequest(SecretResolveRequest.class);
    assertThat(request.getReferences()).containsExactly(REFERENCE, REFERENCE);
  }

  @Test
  void shouldSendMoreReferencesThanTheClusterAccepts() {
    // given the batch size limit is owned by the cluster, which rejects an over-long batch
    gatewayService.onSecretsResolveRequest(new SecretResolveResult());
    final List<String> references =
        IntStream.range(0, 25)
            .mapToObj(index -> "camunda.secrets.reference-" + index)
            .collect(Collectors.toList());

    // when
    client.newResolveSecretsCommand().references(references).send().join();

    // then
    final SecretResolveRequest request = gatewayService.getLastRequest(SecretResolveRequest.class);
    assertThat(request.getReferences()).hasSize(25);
  }

  @Test
  void shouldSendMalformedReferenceUnchanged() {
    // given a malformed reference is a resolution error, not a client-side failure
    gatewayService.onSecretsResolveRequest(new SecretResolveResult());

    // when
    client.newResolveSecretsCommand().reference("not-a-secret-reference").send().join();

    // then
    final SecretResolveRequest request = gatewayService.getLastRequest(SecretResolveRequest.class);
    assertThat(request.getReferences()).containsExactly("not-a-secret-reference");
  }

  @Test
  void shouldMapResolvedSecrets() {
    // given
    gatewayService.onSecretsResolveRequest(
        new SecretResolveResult().addResolvedItem(resolved(REFERENCE, VALUE)));

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().reference(REFERENCE).send().join();

    // then
    assertThat(response.isFullyResolved()).isTrue();
    assertThat(response.getErrors()).isEmpty();
    assertThat(response.getResolved()).hasSize(1);
    assertThat(response.getResolved().get(0).getReference()).isEqualTo(REFERENCE);
    assertThat(response.getResolved().get(0).getValue()).isEqualTo(VALUE);
  }

  @Test
  void shouldNotThrowOnPartialFailure() {
    // given one reference resolves and one is denied
    gatewayService.onSecretsResolveRequest(
        new SecretResolveResult()
            .addResolvedItem(resolved(REFERENCE, VALUE))
            .addErrorsItem(
                error(
                    OTHER_REFERENCE,
                    io.camunda.client.protocol.rest.SecretErrorCode.ACCESS_DENIED,
                    "not authorized")));

    // when
    final AtomicReference<ResolveSecretsResponse> sent = new AtomicReference<>();
    assertThatCode(
            () ->
                sent.set(
                    client
                        .newResolveSecretsCommand()
                        .references(REFERENCE, OTHER_REFERENCE)
                        .send()
                        .join()))
        .doesNotThrowAnyException();

    // then the failure is response data, not an exception
    final ResolveSecretsResponse response = sent.get();
    assertThat(response.isFullyResolved()).isFalse();
    assertThat(response.getResolved()).hasSize(1);
    assertThat(response.getResolved().get(0).getReference()).isEqualTo(REFERENCE);
    assertThat(response.getErrors()).hasSize(1);
    assertThat(response.getErrors().get(0).getReference()).isEqualTo(OTHER_REFERENCE);
    assertThat(response.getErrors().get(0).getCode()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
    assertThat(response.getErrors().get(0).getMessage()).isEqualTo("not authorized");
  }

  @Test
  void shouldNotThrowWhenAllReferencesFail() {
    // given
    gatewayService.onSecretsResolveRequest(
        new SecretResolveResult()
            .addErrorsItem(
                error(
                    REFERENCE,
                    io.camunda.client.protocol.rest.SecretErrorCode.NOT_FOUND,
                    "no secret"))
            .addErrorsItem(
                error(
                    OTHER_REFERENCE,
                    io.camunda.client.protocol.rest.SecretErrorCode.ACCESS_DENIED,
                    "not authorized")));

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().references(REFERENCE, OTHER_REFERENCE).send().join();

    // then
    assertThat(response.isFullyResolved()).isFalse();
    assertThat(response.getResolved()).isEmpty();
    assertThat(response.getErrors()).hasSize(2);
  }

  /**
   * Drives every code the generated protocol enum knows, excluding only the generator's unknown
   * default, which {@link #shouldMapUnknownErrorCodeToUnknownEnumValue} covers. Deliberately not an
   * explicit name list: a code the API contract gains is picked up by the enum source, and the
   * assertion's {@code SecretErrorCode.valueOf(code.name())} then fails until {@link
   * SecretErrorCode} mirrors it. An unmirrored code does not fail the batch at runtime any more, it
   * degrades to {@link SecretErrorCode#UNKNOWN_ENUM_VALUE}, so this test is what keeps the two
   * enums from drifting apart unnoticed.
   */
  @ParameterizedTest
  @EnumSource(
      value = io.camunda.client.protocol.rest.SecretErrorCode.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"UNKNOWN_DEFAULT_OPEN_API"})
  void shouldMapEveryErrorCode(final io.camunda.client.protocol.rest.SecretErrorCode code) {
    // given
    gatewayService.onSecretsResolveRequest(
        new SecretResolveResult().addErrorsItem(error(REFERENCE, code, "failed")));

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().reference(REFERENCE).send().join();

    // then
    assertThat(response.getErrors().get(0).getCode())
        .isEqualTo(SecretErrorCode.valueOf(code.name()));
  }

  @Test
  void shouldMapUnknownErrorCodeToUnknownEnumValue() {
    // given an error code this client version does not know
    gatewayService.onSecretsResolveRequest(
        new SecretResolveResult()
            .addErrorsItem(
                error(
                    REFERENCE,
                    io.camunda.client.protocol.rest.SecretErrorCode.UNKNOWN_DEFAULT_OPEN_API,
                    "failed")));

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().reference(REFERENCE).send().join();

    // then it is reported rather than failing the command
    assertThat(response.getErrors().get(0).getCode()).isEqualTo(SecretErrorCode.UNKNOWN_ENUM_VALUE);
  }

  @Test
  void shouldReturnEmptyListsWhenResponseOmitsArrays() {
    // given
    gatewayService.onSecretsResolveRequest(new SecretResolveResult());

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().reference(REFERENCE).send().join();

    // then
    assertThat(response.getResolved()).isEmpty();
    assertThat(response.getErrors()).isEmpty();
  }

  @Test
  void shouldNotReportFullyResolvedWhenResponseDropsReference() {
    // given a response that accounts for neither the requested reference nor an error for it
    gatewayService.onSecretsResolveRequest(new SecretResolveResult());

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().reference(REFERENCE).send().join();

    // then the caller is not sent to a resolved entry that is not there
    assertThat(response.isFullyResolved()).isFalse();
  }

  @Test
  void shouldNotReportFullyResolvedWhenResponseRepeatsOneReferenceAndDropsAnother() {
    // given a response that returns as many entries as were requested, but for one reference twice
    // and for the other not at all
    gatewayService.onSecretsResolveRequest(
        new SecretResolveResult()
            .addResolvedItem(resolved(REFERENCE, VALUE))
            .addResolvedItem(resolved(REFERENCE, VALUE)));

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().references(REFERENCE, OTHER_REFERENCE).send().join();

    // then the missing reference is detected, rather than masked by the matching entry count
    assertThat(response.isFullyResolved()).isFalse();
  }

  @Test
  void shouldReportFullyResolvedWhenDuplicateReferenceIsResolvedOnce() {
    // given the cluster deduplicates, so a reference requested twice comes back once
    gatewayService.onSecretsResolveRequest(
        new SecretResolveResult().addResolvedItem(resolved(REFERENCE, VALUE)));

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().references(REFERENCE, REFERENCE).send().join();

    // then
    assertThat(response.isFullyResolved()).isTrue();
  }

  @Test
  void shouldReportUnknownEnumValueWhenErrorCodeIsAbsent() {
    // given an error entry without a code
    gatewayService.onSecretsResolveRequest(
        new SecretResolveResult().addErrorsItem(error(REFERENCE, null, "failed")));

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().reference(REFERENCE).send().join();

    // then the code is reported rather than left null for the caller to trip over
    assertThat(response.getErrors().get(0).getCode()).isEqualTo(SecretErrorCode.UNKNOWN_ENUM_VALUE);
  }

  @Test
  void shouldSendEmptyReferenceUnchanged() {
    // given an empty reference is a resolution error like any other malformed one, so it must not
    // fail the references alongside it
    gatewayService.onSecretsResolveRequest(new SecretResolveResult());

    // when
    client.newResolveSecretsCommand().references(REFERENCE, "").send().join();

    // then
    final SecretResolveRequest request = gatewayService.getLastRequest(SecretResolveRequest.class);
    assertThat(request.getReferences()).containsExactly(REFERENCE, "");
  }

  @Test
  void shouldReturnUnmodifiableLists() {
    // given
    gatewayService.onSecretsResolveRequest(
        new SecretResolveResult()
            .addResolvedItem(resolved(REFERENCE, VALUE))
            .addErrorsItem(
                error(
                    OTHER_REFERENCE,
                    io.camunda.client.protocol.rest.SecretErrorCode.NOT_FOUND,
                    "no secret")));

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().references(REFERENCE, OTHER_REFERENCE).send().join();

    // then the response cannot be altered through the lists it hands out
    assertThat(response.getResolved()).isUnmodifiable();
    assertThat(response.getErrors()).isUnmodifiable();
  }

  @Test
  void shouldNotExposeSecretValueInToString() {
    // given
    gatewayService.onSecretsResolveRequest(
        new SecretResolveResult().addResolvedItem(resolved(REFERENCE, VALUE)));

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().reference(REFERENCE).send().join();

    // then
    assertThat(response.toString()).contains(REFERENCE).doesNotContain(VALUE);
    assertThat(response.getResolved().get(0).toString()).contains(REFERENCE).doesNotContain(VALUE);
  }

  @Test
  void shouldNotExposeErrorMessageInToString() {
    // given an error message, which is server-provided free text the client cannot vet
    gatewayService.onSecretsResolveRequest(
        new SecretResolveResult()
            .addErrorsItem(
                error(
                    REFERENCE,
                    io.camunda.client.protocol.rest.SecretErrorCode.NOT_FOUND,
                    "no secret for " + VALUE)));

    // when
    final ResolveSecretsResponse response =
        client.newResolveSecretsCommand().reference(REFERENCE).send().join();

    // then the reference and the typed code remain printable, but the message does not
    final ResolutionError error = response.getErrors().get(0);
    assertThat(error.toString())
        .contains(REFERENCE)
        .contains(SecretErrorCode.NOT_FOUND.name())
        .doesNotContain(VALUE);
    assertThat(response.toString()).doesNotContain(VALUE);

    // and it stays reachable for a caller that wants it
    assertThat(error.getMessage()).contains(VALUE);
  }

  @Test
  void shouldRaiseExceptionOnRejectedRequest() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getSecretsResolveUrl(),
        () -> new ProblemDetail().title("Invalid data").status(400));

    // when / then
    assertThatThrownBy(() -> client.newResolveSecretsCommand().reference(REFERENCE).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400")
        .hasMessageContaining("Invalid data");
  }

  @Test
  void shouldRaiseExceptionWhenUnauthenticated() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getSecretsResolveUrl(),
        () -> new ProblemDetail().title("Unauthorized").status(401));

    // when / then
    assertThatThrownBy(() -> client.newResolveSecretsCommand().reference(REFERENCE).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 401: 'Unauthorized'");
  }

  @Test
  void shouldRaiseExceptionOnServerError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getSecretsResolveUrl(),
        () -> new ProblemDetail().title("Internal Server Error").status(500));

    // when / then
    assertThatThrownBy(() -> client.newResolveSecretsCommand().reference(REFERENCE).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 500: 'Internal Server Error'");
  }

  @Test
  void shouldNotExposeSecretValuesOfAResponseSentUnderAnErrorStatus() {
    // given a cluster or proxy breaking the contract by answering with secrets and an error status
    stubResolveResponse(502, "application/json", secretsBody());

    // when / then the mismatch is reported without the body it could not make sense of
    assertThatThrownBy(() -> client.newResolveSecretsCommand().reference(REFERENCE).send().join())
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("redacted")
        .hasMessageNotContaining(VALUE);
  }

  @Test
  void shouldNotExposeSecretValuesOfAResponseSentUnderAnUnexpectedContentType() {
    // given a cluster or proxy breaking the contract by answering with a non-JSON content type
    stubResolveResponse(200, "text/plain", secretsBody());

    // when / then the raw body is not echoed, as it carries the resolved values verbatim
    assertThatThrownBy(() -> client.newResolveSecretsCommand().reference(REFERENCE).send().join())
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("redacted")
        .hasMessageNotContaining(VALUE);
  }

  @Test
  void shouldRaiseExceptionOnNullReferences() {
    // when / then
    assertThatThrownBy(() -> client.newResolveSecretsCommand().references((List<String>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("references");
  }

  @Test
  void shouldRaiseExceptionOnNullReferencesArray() {
    // when / then
    assertThatThrownBy(() -> client.newResolveSecretsCommand().references((String[]) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("references");
  }

  @Test
  void shouldRaiseExceptionOnNullReferenceEntry() {
    // when / then
    assertThatThrownBy(
            () -> client.newResolveSecretsCommand().references(Collections.singletonList(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reference");
  }

  @Test
  void shouldRaiseExceptionWhenNoReferenceIsSet() {
    // when / then
    assertThatThrownBy(() -> client.newResolveSecretsCommand().send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("references");
  }

  private static void stubResolveResponse(
      final int status, final String contentType, final String body) {
    WireMock.stubFor(
        WireMock.post(WireMock.urlEqualTo(RestGatewayPaths.getSecretsResolveUrl()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", contentType)
                    .withBody(body)));
  }

  private static String secretsBody() {
    return "{\"resolved\":[{\"reference\":\"" + REFERENCE + "\",\"value\":\"" + VALUE + "\"}]}";
  }

  private static ResolvedSecret resolved(final String reference, final String value) {
    return new ResolvedSecret().reference(reference).value(value);
  }

  private static SecretResolutionError error(
      final String reference,
      final io.camunda.client.protocol.rest.SecretErrorCode code,
      final String message) {
    return new SecretResolutionError().reference(reference).code(code).message(message);
  }
}
