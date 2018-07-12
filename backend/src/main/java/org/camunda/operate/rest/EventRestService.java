/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.rest;

import java.util.List;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.es.reader.EventReader;
import org.camunda.operate.rest.dto.EventDto;
import org.camunda.operate.rest.dto.EventQueryDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import static org.camunda.operate.rest.EventRestService.EVENTS_URL;

@Api(tags = {"Zeebe events"})
@SwaggerDefinition(tags = {
  @Tag(name = "Zeebe events", description = "Zeebe events")
})
@RestController
@RequestMapping(value = EVENTS_URL)
public class EventRestService {

  public static final String EVENTS_URL = "/api/events";

  @Autowired
  private EventReader eventReader;

  @ApiOperation("List Zeebe events")
  @PostMapping
  public List<EventDto> getEvents(@RequestBody EventQueryDto eventQuery,
    @RequestParam("firstResult") Integer firstResult,
    @RequestParam("maxResults") Integer maxResults) {
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQuery, firstResult, maxResults);
    return EventDto.createFrom(eventEntities);
  }

}
