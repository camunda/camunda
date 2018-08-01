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
package org.camunda.operate;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.camunda.operate.es.ElasticsearchConnector;
import org.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class JacksonConfig {

  @Autowired
  private OperateProperties operateProperties;

  @Bean("esObjectMapper")
  public ObjectMapper objectMapper() {

    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new ElasticsearchConnector.CustomSerializer(dateTimeFormatter()));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new ElasticsearchConnector.CustomDeserializer(dateTimeFormatter()));

    return Jackson2ObjectMapperBuilder.json().modules(javaTimeModule)
      .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
      .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS, SerializationFeature.INDENT_OUTPUT).build();
  }

  @Bean
  public DateTimeFormatter dateTimeFormatter() {
    return DateTimeFormatter.ofPattern(operateProperties.getElasticsearch().getDateFormat());
  }


}
