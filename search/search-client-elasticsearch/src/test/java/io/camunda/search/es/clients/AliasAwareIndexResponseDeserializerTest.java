/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class AliasAwareIndexResponseDeserializerTest {

  @Test
  void shouldDeserializeShardFailureWithUnderscoreShardAlias() {
    // given - JSON using "_shard" alias
    final var json =
        """
        {
          "_shard": 2,
          "_index": "my-index",
          "_node": "node1",
          "reason": {"type": "i_o_exception", "reason": "No space left on device"},
          "status": "INTERNAL_SERVER_ERROR"
        }
        """;
    final var mapper = new JacksonJsonpMapper();
    final var parser = mapper.jsonProvider().createParser(new StringReader(json));

    // when
    final var failure =
        AliasAwareIndexResponseDeserializer.SHARD_FAILURE.deserialize(parser, mapper);

    // then - underscore-aliased fields are recognized
    assertThat(failure).isNotNull();
    assertThat(failure.shard()).isEqualTo(2);
    assertThat(failure.index()).isEqualTo("my-index");
    assertThat(failure.node()).isEqualTo("node1");
    assertThat(failure.status()).isEqualTo("INTERNAL_SERVER_ERROR");
  }

  @Test
  void shouldDeserializeIndexResponseWithUnderscoreShardAlias() {
    // given - a full IndexResponse JSON where shard failure uses underscore-prefixed aliases
    final var json =
        """
        {
          "_id": "foo",
          "_index": "bar",
          "result": "created",
          "_primary_term": 1,
          "_seq_no": 1,
          "_version": 1,
          "_shards": {
            "total": 2,
            "successful": 1,
            "failed": 1,
            "failures": [
              {
                "_shard": 3,
                "_index": "bar",
                "_node": "node1",
                "reason": {"type": "i_o_exception", "reason": "No space left on device"},
                "status": "INTERNAL_SERVER_ERROR"
              }
            ]
          }
        }
        """;
    final var mapper = new JacksonJsonpMapper();
    final var parser = mapper.jsonProvider().createParser(new StringReader(json));

    // when
    final var response =
        AliasAwareIndexResponseDeserializer.INDEX_RESPONSE.deserialize(parser, mapper);

    // then
    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo("foo");
    assertThat(response.shards().failures()).hasSize(1);
    assertThat(response.shards().failures().get(0).shard()).isEqualTo(3);
  }
}
