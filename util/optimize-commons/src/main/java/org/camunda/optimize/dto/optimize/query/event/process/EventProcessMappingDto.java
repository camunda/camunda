/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@FieldNameConstants
public class EventProcessMappingDto implements OptimizeDto {
  @EqualsAndHashCode.Include
  private String id;
  @NotBlank
  private String name;

  private String lastModifier;

  private OffsetDateTime lastModified;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String xml;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Valid
  private Map<String, EventMappingDto> mappings;

  private List<EventProcessRoleRequestDto<IdentityDto>> roles;

  private EventProcessState state;

  private Double publishingProgress;

  @Builder.Default
  private List<EventSourceEntryDto<?>> eventSources = new ArrayList<>();
}
