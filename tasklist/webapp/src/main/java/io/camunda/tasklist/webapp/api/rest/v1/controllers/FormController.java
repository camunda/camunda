/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Form", description = "API to query forms.")
@RestController
@RequestMapping(value = TasklistURIs.FORMS_URL_V1, produces = MediaType.APPLICATION_JSON_VALUE)
public class FormController extends ApiErrorController {

  @Autowired private FormStore formStore;

  @Operation(
      summary = "Get a form",
      description =
          "Get the form details by `formId` and `processDefinitionKey` required query param. The `version` query param is optional and is used only for deployed forms (if empty, it retrieves the highest version).",
      responses = {
        @ApiResponse(
            description = "On success returned.",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the form with the `formId` and `processDefinitionKey` is not found.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @GetMapping("{formId}")
  public ResponseEntity<FormResponse> getForm(
      @PathVariable @Parameter(description = "The ID of the form.", required = true) String formId,
      @RequestParam
          @Parameter(description = "Reference to the process definition.", required = true)
          String processDefinitionKey,
      @RequestParam(required = false)
          @Parameter(
              description = "The version of the form. Valid only for deployed forms.",
              required = false)
          Long version) {
    final var form = formStore.getForm(formId, processDefinitionKey, version);

    // This is to set processDefinitionKey when the form is deployed
    if (form.getProcessDefinitionId() == null) {
      form.setProcessDefinitionId(processDefinitionKey);
    }

    return ResponseEntity.ok(FormResponse.fromFormEntity(form));
  }
}
