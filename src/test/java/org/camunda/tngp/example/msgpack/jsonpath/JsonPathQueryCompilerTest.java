package org.camunda.tngp.example.msgpack.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.example.msgpack.impl.newidea.ArrayIndexFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.MapValueWithKeyFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.RootCollectionFilter;
import org.junit.Test;

public class JsonPathQueryCompilerTest
{

    @Test
    public void testQueryCompiler()
    {
        // given
        JsonPathQueryCompiler compiler = new JsonPathQueryCompiler();

        // when
        JsonPathQuery jsonPathQuery = compiler.compile("$.key1.key2[1].key3");

        // then
        assertThat(jsonPathQuery.getSize()).isEqualTo(5);
        assertThat(jsonPathQuery.getFilter(0)).isInstanceOf(RootCollectionFilter.class);
        assertThat(jsonPathQuery.getFilter(1)).isInstanceOf(MapValueWithKeyFilter.class);
        assertThat(jsonPathQuery.getFilter(2)).isInstanceOf(MapValueWithKeyFilter.class);
        assertThat(jsonPathQuery.getFilter(3)).isInstanceOf(ArrayIndexFilter.class);
        assertThat(jsonPathQuery.getFilter(4)).isInstanceOf(MapValueWithKeyFilter.class);
    }
}
