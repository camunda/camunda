package org.camunda.tngp.msgpack.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.msgpack.filter.ArrayIndexFilter;
import org.camunda.tngp.msgpack.filter.MapValueWithKeyFilter;
import org.camunda.tngp.msgpack.filter.MsgPackFilter;
import org.camunda.tngp.msgpack.filter.RootCollectionFilter;
import org.camunda.tngp.msgpack.filter.WildcardFilter;
import org.camunda.tngp.msgpack.query.MsgPackFilterContext;
import org.junit.Test;

public class JsonPathQueryCompilerTest
{

    @Test
    public void testQueryCompiler()
    {
        // given
        final JsonPathQueryCompiler compiler = new JsonPathQueryCompiler();

        // when
        final JsonPathQuery jsonPathQuery = compiler.compile("$.key1.key2[1].key3");

        // then
        final MsgPackFilter[] filters = jsonPathQuery.getFilters();
        assertThat(filters).hasSize(4);
        // note: these assertions are stricter than necessary;
        // all we need as that each filter is once in the list and
        // that each instance references the correct filter
        assertThat(filters[0]).isInstanceOf(RootCollectionFilter.class);
        assertThat(filters[1]).isInstanceOf(MapValueWithKeyFilter.class);
        assertThat(filters[2]).isInstanceOf(ArrayIndexFilter.class);
        assertThat(filters[3]).isInstanceOf(WildcardFilter.class);

        final MsgPackFilterContext filterInstances = jsonPathQuery.getFilterInstances();
        assertThat(filterInstances.size()).isEqualTo(5);

        assertFilterAtPosition(filterInstances, 0, 0);
        assertFilterAtPosition(filterInstances, 1, 1);
        assertFilterAtPosition(filterInstances, 2, 1);
        assertFilterAtPosition(filterInstances, 3, 2);
        assertFilterAtPosition(filterInstances, 4, 1);
    }

    @Test
    public void testWildcardCompilation()
    {
        // given
        final JsonPathQueryCompiler compiler = new JsonPathQueryCompiler();

        // when
        final JsonPathQuery jsonPathQuery = compiler.compile("$.*");

        // then
        final MsgPackFilter[] filters = jsonPathQuery.getFilters();
        assertThat(filters).hasSize(4);
        assertThat(filters[0]).isInstanceOf(RootCollectionFilter.class);
        assertThat(filters[3]).isInstanceOf(WildcardFilter.class);

        final MsgPackFilterContext filterInstances = jsonPathQuery.getFilterInstances();
        assertFilterAtPosition(filterInstances, 0, 0);
        assertFilterAtPosition(filterInstances, 1, 3);
    }

    protected static void assertFilterAtPosition(MsgPackFilterContext filterInstances, int position, int expectedFilterId)
    {
        filterInstances.moveTo(position);
        assertThat(filterInstances.filterId()).isEqualTo(expectedFilterId);
    }
}
