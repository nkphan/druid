/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.segment.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.apache.druid.common.config.NullHandling;
import org.apache.druid.error.DruidException;
import org.apache.druid.guice.NestedDataModule;
import org.apache.druid.jackson.DefaultObjectMapper;
import org.apache.druid.java.util.common.Pair;
import org.apache.druid.math.expr.ExprEval;
import org.apache.druid.math.expr.ExpressionType;
import org.apache.druid.query.filter.EqualityFilter;
import org.apache.druid.query.filter.FilterTuning;
import org.apache.druid.segment.IndexBuilder;
import org.apache.druid.segment.StorageAdapter;
import org.apache.druid.segment.column.ColumnType;
import org.apache.druid.testing.InitializedNullHandlingTest;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.Closeable;
import java.util.Arrays;

@RunWith(Enclosed.class)
public class EqualityFilterTests
{
  @RunWith(Parameterized.class)
  public static class EqualityFilterTest extends BaseFilterTest
  {
    public EqualityFilterTest(
        String testName,
        IndexBuilder indexBuilder,
        Function<IndexBuilder, Pair<StorageAdapter, Closeable>> finisher,
        boolean cnf,
        boolean optimize
    )
    {
      super(testName, DEFAULT_ROWS, indexBuilder, finisher, cnf, optimize);
    }

    @AfterClass
    public static void tearDown() throws Exception
    {
      BaseFilterTest.tearDown(EqualityFilterTest.class.getName());
    }


    @Test
    public void testSingleValueStringColumnWithoutNulls()
    {
      if (NullHandling.sqlCompatible()) {
        assertFilterMatches(new EqualityFilter("dim0", ColumnType.STRING, "", null), ImmutableList.of());
      }
      assertFilterMatches(new EqualityFilter("dim0", ColumnType.STRING, "0", null), ImmutableList.of("0"));
      assertFilterMatches(new EqualityFilter("dim0", ColumnType.STRING, "1", null), ImmutableList.of("1"));

      assertFilterMatches(new EqualityFilter("dim0", ColumnType.LONG, 0L, null), ImmutableList.of("0"));
      assertFilterMatches(new EqualityFilter("dim0", ColumnType.LONG, 1L, null), ImmutableList.of("1"));
    }

    @Test
    public void testSingleValueVirtualStringColumnWithoutNulls()
    {
      if (NullHandling.sqlCompatible()) {
        assertFilterMatches(new EqualityFilter("vdim0", ColumnType.STRING, "", null), ImmutableList.of());
      }
      assertFilterMatches(new EqualityFilter("vdim0", ColumnType.STRING, "0", null), ImmutableList.of("0"));
      assertFilterMatches(new EqualityFilter("vdim0", ColumnType.STRING, "1", null), ImmutableList.of("1"));
      assertFilterMatches(new EqualityFilter("vdim0", ColumnType.LONG, 0L, null), ImmutableList.of("0"));
      assertFilterMatches(new EqualityFilter("vdim0", ColumnType.LONG, 1L, null), ImmutableList.of("1"));
    }

    @Test
    public void testListFilteredVirtualColumn()
    {
      assertFilterMatchesSkipVectorize(
          new EqualityFilter("allow-dim0", ColumnType.STRING, "1", null),
          ImmutableList.of()
      );
      assertFilterMatchesSkipVectorize(
          new EqualityFilter("allow-dim0", ColumnType.STRING, "4", null),
          ImmutableList.of("4")
      );
      assertFilterMatchesSkipVectorize(
          new EqualityFilter("deny-dim0", ColumnType.STRING, "0", null),
          ImmutableList.of("0")
      );
      assertFilterMatchesSkipVectorize(
          new EqualityFilter("deny-dim0", ColumnType.STRING, "4", null),
          ImmutableList.of()
      );

      // auto ingests arrays instead of MVDs which dont work with list filtered virtual column
      if (!isAutoSchema()) {
        assertFilterMatchesSkipVectorize(
            new EqualityFilter("allow-dim2", ColumnType.STRING, "b", null),
            ImmutableList.of()
        );
        assertFilterMatchesSkipVectorize(
            new EqualityFilter("allow-dim2", ColumnType.STRING, "a", null),
            ImmutableList.of("0", "3")
        );
        assertFilterMatchesSkipVectorize(
            new EqualityFilter("deny-dim2", ColumnType.STRING, "b", null),
            ImmutableList.of("0")
        );
        assertFilterMatchesSkipVectorize(
            new EqualityFilter("deny-dim2", ColumnType.STRING, "a", null),
            ImmutableList.of()
        );
      }
    }

    @Test
    public void testSingleValueStringColumnWithNulls()
    {
      if (NullHandling.sqlCompatible()) {
        assertFilterMatches(new EqualityFilter("dim1", ColumnType.STRING, "", null), ImmutableList.of("0"));
      }
      assertFilterMatches(new EqualityFilter("dim1", ColumnType.STRING, "10", null), ImmutableList.of("1"));
      assertFilterMatches(new EqualityFilter("dim1", ColumnType.STRING, "2", null), ImmutableList.of("2"));
      assertFilterMatches(new EqualityFilter("dim1", ColumnType.STRING, "1", null), ImmutableList.of("3"));
      assertFilterMatches(new EqualityFilter("dim1", ColumnType.STRING, "abdef", null), ImmutableList.of("4"));
      assertFilterMatches(new EqualityFilter("dim1", ColumnType.STRING, "abc", null), ImmutableList.of("5"));
      assertFilterMatches(new EqualityFilter("dim1", ColumnType.STRING, "ab", null), ImmutableList.of());
    }

    @Test
    public void testSingleValueVirtualStringColumnWithNulls()
    {
      // testSingleValueStringColumnWithNulls but with virtual column selector
      if (NullHandling.sqlCompatible()) {
        assertFilterMatches(new EqualityFilter("vdim1", ColumnType.STRING, "", null), ImmutableList.of("0"));
      }
      assertFilterMatches(new EqualityFilter("vdim1", ColumnType.STRING, "10", null), ImmutableList.of("1"));
      assertFilterMatches(new EqualityFilter("vdim1", ColumnType.STRING, "2", null), ImmutableList.of("2"));
      assertFilterMatches(new EqualityFilter("vdim1", ColumnType.STRING, "1", null), ImmutableList.of("3"));
      assertFilterMatches(new EqualityFilter("vdim1", ColumnType.STRING, "abdef", null), ImmutableList.of("4"));
      assertFilterMatches(new EqualityFilter("vdim1", ColumnType.STRING, "abc", null), ImmutableList.of("5"));
      assertFilterMatches(new EqualityFilter("vdim1", ColumnType.STRING, "ab", null), ImmutableList.of());
    }

    @Test
    public void testMultiValueStringColumn()
    {
      if (isAutoSchema()) {
        // auto ingests arrays instead of strings
        // single values are implicitly upcast to single element arrays, so we get some matches here...
        if (NullHandling.sqlCompatible()) {
          assertFilterMatches(new EqualityFilter("dim2", ColumnType.STRING, "", null), ImmutableList.of("2"));
        }
        assertFilterMatches(new EqualityFilter("dim2", ColumnType.STRING, "a", null), ImmutableList.of("3"));
        assertFilterMatches(new EqualityFilter("dim2", ColumnType.STRING, "b", null), ImmutableList.of());
        assertFilterMatches(new EqualityFilter("dim2", ColumnType.STRING, "c", null), ImmutableList.of("4"));
        assertFilterMatches(new EqualityFilter("dim2", ColumnType.STRING, "d", null), ImmutableList.of());

        // array matchers can match the whole array
        if (NullHandling.sqlCompatible()) {
          assertFilterMatches(
              new EqualityFilter("dim2", ColumnType.STRING, ImmutableList.of(""), null),
              ImmutableList.of("2")
          );
        }
        assertFilterMatches(
            new EqualityFilter("dim2", ColumnType.STRING_ARRAY, new Object[]{"a", "b"}, null),
            ImmutableList.of("0")
        );
        assertFilterMatches(
            new EqualityFilter("dim2", ColumnType.STRING_ARRAY, ImmutableList.of("a", "b"), null),
            ImmutableList.of("0")
        );
        assertFilterMatches(
            new EqualityFilter("dim2", ColumnType.STRING_ARRAY, new Object[]{"a"}, null),
            ImmutableList.of("3")
        );
        assertFilterMatches(
            new EqualityFilter("dim2", ColumnType.STRING_ARRAY, new Object[]{"b"}, null),
            ImmutableList.of()
        );
        assertFilterMatches(
            new EqualityFilter("dim2", ColumnType.STRING_ARRAY, new Object[]{"c"}, null),
            ImmutableList.of("4")
        );
        assertFilterMatches(
            new EqualityFilter("dim2", ColumnType.STRING_ARRAY, new Object[]{"d"}, null),
            ImmutableList.of()
        );
      } else {
        if (NullHandling.sqlCompatible()) {
          assertFilterMatches(new EqualityFilter("dim2", ColumnType.STRING, "", null), ImmutableList.of("2"));
        }
        assertFilterMatches(
            new EqualityFilter("dim2", ColumnType.STRING, "a", null),
            ImmutableList.of("0", "3")
        );
        assertFilterMatches(new EqualityFilter("dim2", ColumnType.STRING, "b", null), ImmutableList.of("0"));
        assertFilterMatches(new EqualityFilter("dim2", ColumnType.STRING, "c", null), ImmutableList.of("4"));
        assertFilterMatches(new EqualityFilter("dim2", ColumnType.STRING, "d", null), ImmutableList.of());
      }
    }

    @Test
    public void testMissingColumnSpecifiedInDimensionList()
    {
      if (NullHandling.sqlCompatible()) {
        assertFilterMatches(new EqualityFilter("dim3", ColumnType.STRING, "", null), ImmutableList.of());
      }
      assertFilterMatches(new EqualityFilter("dim3", ColumnType.STRING, "a", null), ImmutableList.of());
      assertFilterMatches(new EqualityFilter("dim3", ColumnType.STRING, "b", null), ImmutableList.of());
      assertFilterMatches(new EqualityFilter("dim3", ColumnType.STRING, "c", null), ImmutableList.of());
    }

    @Test
    public void testMissingColumnNotSpecifiedInDimensionList()
    {
      if (NullHandling.sqlCompatible()) {
        assertFilterMatches(new EqualityFilter("dim4", ColumnType.STRING, "", null), ImmutableList.of());
      }
      assertFilterMatches(new EqualityFilter("dim4", ColumnType.STRING, "a", null), ImmutableList.of());
      assertFilterMatches(new EqualityFilter("dim4", ColumnType.STRING, "b", null), ImmutableList.of());
      assertFilterMatches(new EqualityFilter("dim4", ColumnType.STRING, "c", null), ImmutableList.of());
    }

    @Test
    public void testExpressionVirtualColumn()
    {
      assertFilterMatches(
          new EqualityFilter("expr", ColumnType.STRING, "1.1", null),
          ImmutableList.of("0", "1", "2", "3", "4", "5")
      );
      assertFilterMatches(new EqualityFilter("expr", ColumnType.STRING, "1.2", null), ImmutableList.of());

      assertFilterMatches(
          new EqualityFilter("expr", ColumnType.FLOAT, 1.1f, null),
          ImmutableList.of("0", "1", "2", "3", "4", "5")
      );
      assertFilterMatches(new EqualityFilter("expr", ColumnType.FLOAT, 1.2f, null), ImmutableList.of());

      assertFilterMatches(
          new EqualityFilter("expr", ColumnType.DOUBLE, 1.1, null),
          ImmutableList.of("0", "1", "2", "3", "4", "5")
      );
      assertFilterMatches(new EqualityFilter("expr", ColumnType.DOUBLE, 1.2, null), ImmutableList.of());

      // if we accidentally specify it as a string, it works too...
      assertFilterMatches(
          new EqualityFilter("expr", ColumnType.DOUBLE, "1.1", null),
          ImmutableList.of("0", "1", "2", "3", "4", "5")
      );
      assertFilterMatches(new EqualityFilter("expr", ColumnType.DOUBLE, "1.2", null), ImmutableList.of());
    }

    @Test
    public void testNumericColumnNullsAndDefaults()
    {
      if (canTestNumericNullsAsDefaultValues) {
        assertFilterMatches(new EqualityFilter("f0", ColumnType.FLOAT, 0f, null), ImmutableList.of("0", "4"));
        assertFilterMatches(new EqualityFilter("d0", ColumnType.DOUBLE, 0.0, null), ImmutableList.of("0", "2"));
        assertFilterMatches(new EqualityFilter("l0", ColumnType.LONG, 0L, null), ImmutableList.of("0", "3"));
        assertFilterMatches(new EqualityFilter("f0", ColumnType.STRING, "0", null), ImmutableList.of("0", "4"));
        assertFilterMatches(new EqualityFilter("d0", ColumnType.STRING, "0", null), ImmutableList.of("0", "2"));
        assertFilterMatches(new EqualityFilter("l0", ColumnType.STRING, "0", null), ImmutableList.of("0", "3"));
      } else {
        assertFilterMatches(new EqualityFilter("f0", ColumnType.FLOAT, 0f, null), ImmutableList.of("0"));
        assertFilterMatches(new EqualityFilter("d0", ColumnType.DOUBLE, 0.0, null), ImmutableList.of("0"));
        assertFilterMatches(new EqualityFilter("l0", ColumnType.LONG, 0L, null), ImmutableList.of("0"));
        assertFilterMatches(new EqualityFilter("f0", ColumnType.STRING, "0", null), ImmutableList.of("0"));
        assertFilterMatches(new EqualityFilter("d0", ColumnType.STRING, "0", null), ImmutableList.of("0"));
        assertFilterMatches(new EqualityFilter("l0", ColumnType.STRING, "0", null), ImmutableList.of("0"));
      }
    }

    @Test
    public void testVirtualNumericColumnNullsAndDefaults()
    {
      if (canTestNumericNullsAsDefaultValues) {
        assertFilterMatches(new EqualityFilter("vf0", ColumnType.FLOAT, 0f, null), ImmutableList.of("0", "4"));
        assertFilterMatches(new EqualityFilter("vd0", ColumnType.DOUBLE, 0.0, null), ImmutableList.of("0", "2"));
        assertFilterMatches(new EqualityFilter("vl0", ColumnType.LONG, 0L, null), ImmutableList.of("0", "3"));
        assertFilterMatches(new EqualityFilter("vf0", ColumnType.STRING, "0", null), ImmutableList.of("0", "4"));
        assertFilterMatches(new EqualityFilter("vd0", ColumnType.STRING, "0", null), ImmutableList.of("0", "2"));
        assertFilterMatches(new EqualityFilter("vl0", ColumnType.STRING, "0", null), ImmutableList.of("0", "3"));
      } else {
        assertFilterMatches(new EqualityFilter("vf0", ColumnType.FLOAT, 0f, null), ImmutableList.of("0"));
        assertFilterMatches(new EqualityFilter("vd0", ColumnType.DOUBLE, 0.0, null), ImmutableList.of("0"));
        assertFilterMatches(new EqualityFilter("vl0", ColumnType.LONG, 0L, null), ImmutableList.of("0"));
        assertFilterMatches(new EqualityFilter("vf0", ColumnType.STRING, "0", null), ImmutableList.of("0"));
        assertFilterMatches(new EqualityFilter("vd0", ColumnType.STRING, "0", null), ImmutableList.of("0"));
        assertFilterMatches(new EqualityFilter("vl0", ColumnType.STRING, "0", null), ImmutableList.of("0"));
      }
    }

    @Test
    public void testNumeric()
    {
    /*
        dim0   d0         f0        l0
        "0" .. 0.0,       0.0f,     0L
        "1" .. 10.1,      10.1f,    100L
        "2" .. null,      5.5f,     40L
        "3" .. 120.0245,  110.0f,   null
        "4" .. 60.0,      null,     9001L
        "5" .. 765.432,   123.45f,  12345L
     */

      assertFilterMatches(new EqualityFilter("d0", ColumnType.DOUBLE, 10.1, null), ImmutableList.of("1"));
      assertFilterMatches(new EqualityFilter("d0", ColumnType.DOUBLE, 120.0245, null), ImmutableList.of("3"));
      assertFilterMatches(new EqualityFilter("d0", ColumnType.DOUBLE, 765.432, null), ImmutableList.of("5"));
      assertFilterMatches(new EqualityFilter("d0", ColumnType.DOUBLE, 765.431, null), ImmutableList.of());

      assertFilterMatches(new EqualityFilter("l0", ColumnType.LONG, 100L, null), ImmutableList.of("1"));
      assertFilterMatches(new EqualityFilter("l0", ColumnType.LONG, 40L, null), ImmutableList.of("2"));
      assertFilterMatches(new EqualityFilter("l0", ColumnType.LONG, 9001L, null), ImmutableList.of("4"));
      assertFilterMatches(new EqualityFilter("l0", ColumnType.LONG, 9000L, null), ImmutableList.of());
      if (!isAutoSchema()) {
        // auto schema doesn't store float columns as floats, rather they are stored as doubles... the predicate matcher
        // matches fine, but the string value set index does not match correctly if we expect the input float values
        assertFilterMatches(new EqualityFilter("f0", ColumnType.FLOAT, 10.1f, null), ImmutableList.of("1"));
        assertFilterMatches(new EqualityFilter("f0", ColumnType.FLOAT, 110.0f, null), ImmutableList.of("3"));
        assertFilterMatches(new EqualityFilter("f0", ColumnType.FLOAT, 123.45f, null), ImmutableList.of("5"));
        assertFilterMatches(new EqualityFilter("f0", ColumnType.FLOAT, 123.46f, null), ImmutableList.of());
      } else {
        // .. so we need to cast them instead
        assertFilterMatches(
            new EqualityFilter("f0", ColumnType.DOUBLE, (double) 10.1f, null),
            ImmutableList.of("1")
        );
        assertFilterMatches(
            new EqualityFilter("f0", ColumnType.DOUBLE, (double) 110.0f, null),
            ImmutableList.of("3")
        );
        assertFilterMatches(
            new EqualityFilter("f0", ColumnType.DOUBLE, (double) 123.45f, null),
            ImmutableList.of("5")
        );
        assertFilterMatches(
            new EqualityFilter("f0", ColumnType.DOUBLE, (double) 123.46f, null),
            ImmutableList.of()
        );
      }
    }

    @Test
    public void testArrays()
    {
      if (isAutoSchema()) {
        // only auto schema supports array columns... skip other segment types
        /*
            dim0 .. arrayString               arrayLong             arrayDouble
            "0", .. ["a", "b", "c"],          [1L, 2L, 3L],         [1.1, 2.2, 3.3]
            "1", .. [],                       [],                   [1.1, 2.2, 3.3]
            "2", .. null,                     [1L, 2L, 3L],         [null]
            "3", .. ["a", "b", "c"],          null,                 []
            "4", .. ["c", "d"],               [null],               [-1.1, -333.3]
            "5", .. [null],                   [123L, 345L],         null
         */

        assertFilterMatches(
            new EqualityFilter(
                "arrayString",
                ColumnType.STRING_ARRAY,
                ImmutableList.of("a", "b", "c"),
                null
            ),
            ImmutableList.of("0", "3")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayString",
                ColumnType.STRING_ARRAY,
                new Object[]{"a", "b", "c"},
                null
            ),
            ImmutableList.of("0", "3")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayString",
                ColumnType.STRING_ARRAY,
                ImmutableList.of(),
                null
            ),
            ImmutableList.of("1")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayString",
                ColumnType.STRING_ARRAY,
                new Object[]{null},
                null
            ),
            ImmutableList.of("5")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayString",
                ColumnType.STRING_ARRAY,
                new Object[]{null, null},
                null
            ),
            ImmutableList.of()
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayLong",
                ColumnType.LONG_ARRAY,
                ImmutableList.of(1L, 2L, 3L),
                null
            ),
            ImmutableList.of("0", "2")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayLong",
                ColumnType.LONG_ARRAY,
                new Object[]{1L, 2L, 3L},
                null
            ),
            ImmutableList.of("0", "2")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayLong",
                ColumnType.LONG_ARRAY,
                ImmutableList.of(),
                null
            ),
            ImmutableList.of("1")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayLong",
                ColumnType.LONG_ARRAY,
                new Object[]{null},
                null
            ),
            ImmutableList.of("4")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayLong",
                ColumnType.LONG_ARRAY,
                new Object[]{null, null},
                null
            ),
            ImmutableList.of()
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayDouble",
                ColumnType.DOUBLE_ARRAY,
                ImmutableList.of(1.1, 2.2, 3.3),
                null
            ),
            ImmutableList.of("0", "1")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayDouble",
                ColumnType.DOUBLE_ARRAY,
                new Object[]{1.1, 2.2, 3.3},
                null
            ),
            ImmutableList.of("0", "1")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayDouble",
                ColumnType.DOUBLE_ARRAY,
                ImmutableList.of(),
                null
            ),
            ImmutableList.of("3")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayDouble",
                ColumnType.DOUBLE_ARRAY,
                new Object[]{null},
                null
            ),
            ImmutableList.of("2")
        );
        assertFilterMatches(
            new EqualityFilter(
                "arrayDouble",
                ColumnType.DOUBLE_ARRAY,
                ImmutableList.of(1.1, 2.2, 3.4),
                null
            ),
            ImmutableList.of()
        );
      }
    }
  }

  public static class EqualityFilterNonParameterizedTests extends InitializedNullHandlingTest
  {
    @Test
    public void testSerde() throws JsonProcessingException
    {
      ObjectMapper mapper = new DefaultObjectMapper();
      EqualityFilter filter = new EqualityFilter("x", ColumnType.STRING, "hello", null);
      String s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));

      filter = new EqualityFilter("x", ColumnType.LONG, 1L, null);
      s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));

      filter = new EqualityFilter("x", ColumnType.LONG, 1, null);
      s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));

      filter = new EqualityFilter("x", ColumnType.DOUBLE, 111.111, null);
      s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));

      filter = new EqualityFilter("x", ColumnType.FLOAT, 1234.0f, null);
      s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));

      filter = new EqualityFilter("x", ColumnType.STRING_ARRAY, new Object[]{"a", "b", null, "c"}, null);
      s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));

      filter = new EqualityFilter("x", ColumnType.STRING_ARRAY, Arrays.asList("a", "b", null, "c"), null);
      s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));

      filter = new EqualityFilter("x", ColumnType.LONG_ARRAY, new Object[]{1L, null, 2L, 3L}, null);
      s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));

      filter = new EqualityFilter("x", ColumnType.LONG_ARRAY, Arrays.asList(1L, null, 2L, 3L), null);
      s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));

      filter = new EqualityFilter("x", ColumnType.DOUBLE_ARRAY, new Object[]{1.1, 2.1, null, 3.1}, null);
      s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));

      filter = new EqualityFilter("x", ColumnType.DOUBLE_ARRAY, Arrays.asList(1.1, 2.1, null, 3.1), null);
      s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));

      filter = new EqualityFilter("x", ColumnType.NESTED_DATA, ImmutableMap.of("x", ImmutableList.of(1, 2, 3)), null);
      s = mapper.writeValueAsString(filter);
      Assert.assertEquals(filter, mapper.readValue(s, EqualityFilter.class));
    }

    @Test
    public void testGetCacheKey()
    {
      EqualityFilter f1 = new EqualityFilter("x", ColumnType.STRING, "hello", null);
      EqualityFilter f1_2 = new EqualityFilter("x", ColumnType.STRING, "hello", null);
      EqualityFilter f2 = new EqualityFilter("x", ColumnType.STRING, "world", null);
      EqualityFilter f3 = new EqualityFilter("x", ColumnType.STRING, "hello", new FilterTuning(true, null, null));
      Assert.assertArrayEquals(f1.getCacheKey(), f1_2.getCacheKey());
      Assert.assertFalse(Arrays.equals(f1.getCacheKey(), f2.getCacheKey()));
      Assert.assertArrayEquals(f1.getCacheKey(), f3.getCacheKey());

      f1 = new EqualityFilter("x", ColumnType.LONG, 1L, null);
      f1_2 = new EqualityFilter("x", ColumnType.LONG, 1, null);
      f2 = new EqualityFilter("x", ColumnType.LONG, 2L, null);
      f3 = new EqualityFilter("x", ColumnType.LONG, 1L, new FilterTuning(true, null, null));
      Assert.assertArrayEquals(f1.getCacheKey(), f1_2.getCacheKey());
      Assert.assertFalse(Arrays.equals(f1.getCacheKey(), f2.getCacheKey()));
      Assert.assertArrayEquals(f1.getCacheKey(), f3.getCacheKey());

      f1 = new EqualityFilter("x", ColumnType.DOUBLE, 1.1, null);
      f1_2 = new EqualityFilter("x", ColumnType.DOUBLE, 1.1, null);
      f2 = new EqualityFilter("x", ColumnType.DOUBLE, 2.2, null);
      f3 = new EqualityFilter("x", ColumnType.DOUBLE, 1.1, new FilterTuning(true, null, null));
      Assert.assertArrayEquals(f1.getCacheKey(), f1_2.getCacheKey());
      Assert.assertFalse(Arrays.equals(f1.getCacheKey(), f2.getCacheKey()));
      Assert.assertArrayEquals(f1.getCacheKey(), f3.getCacheKey());

      f1 = new EqualityFilter("x", ColumnType.FLOAT, 1.1f, null);
      f1_2 = new EqualityFilter("x", ColumnType.FLOAT, 1.1f, null);
      f2 = new EqualityFilter("x", ColumnType.FLOAT, 2.2f, null);
      f3 = new EqualityFilter("x", ColumnType.FLOAT, 1.1f, new FilterTuning(true, null, null));
      Assert.assertArrayEquals(f1.getCacheKey(), f1_2.getCacheKey());
      Assert.assertFalse(Arrays.equals(f1.getCacheKey(), f2.getCacheKey()));
      Assert.assertArrayEquals(f1.getCacheKey(), f3.getCacheKey());

      f1 = new EqualityFilter("x", ColumnType.STRING_ARRAY, new Object[]{"a", "b", null, "c"}, null);
      f1_2 = new EqualityFilter("x", ColumnType.STRING_ARRAY, Arrays.asList("a", "b", null, "c"), null);
      f2 = new EqualityFilter("x", ColumnType.STRING_ARRAY, new Object[]{"a", "b", "c"}, null);
      f3 = new EqualityFilter(
          "x",
          ColumnType.STRING_ARRAY,
          new Object[]{"a", "b", null, "c"},
          new FilterTuning(true, null, null)
      );
      Assert.assertArrayEquals(f1.getCacheKey(), f1_2.getCacheKey());
      Assert.assertFalse(Arrays.equals(f1.getCacheKey(), f2.getCacheKey()));
      Assert.assertArrayEquals(f1.getCacheKey(), f3.getCacheKey());

      f1 = new EqualityFilter("x", ColumnType.LONG_ARRAY, new Object[]{100L, 200L, null, 300L}, null);
      f1_2 = new EqualityFilter("x", ColumnType.LONG_ARRAY, Arrays.asList(100L, 200L, null, 300L), null);
      f2 = new EqualityFilter("x", ColumnType.LONG_ARRAY, new Object[]{100L, null, 200L, 300L}, null);
      f3 = new EqualityFilter(
          "x",
          ColumnType.LONG_ARRAY,
          new Object[]{100L, 200L, null, 300L},
          new FilterTuning(true, null, null)
      );
      Assert.assertArrayEquals(f1.getCacheKey(), f1_2.getCacheKey());
      Assert.assertFalse(Arrays.equals(f1.getCacheKey(), f2.getCacheKey()));
      Assert.assertArrayEquals(f1.getCacheKey(), f3.getCacheKey());

      f1 = new EqualityFilter("x", ColumnType.DOUBLE_ARRAY, new Object[]{1.001, null, 20.0002, 300.0003}, null);
      f1_2 = new EqualityFilter("x", ColumnType.DOUBLE_ARRAY, Arrays.asList(1.001, null, 20.0002, 300.0003), null);
      f2 = new EqualityFilter("x", ColumnType.DOUBLE_ARRAY, new Object[]{1.001, 20.0002, 300.0003, null}, null);
      f3 = new EqualityFilter(
          "x",
          ColumnType.DOUBLE_ARRAY,
          new Object[]{1.001, null, 20.0002, 300.0003},
          new FilterTuning(true, null, null)
      );
      Assert.assertArrayEquals(f1.getCacheKey(), f1_2.getCacheKey());
      Assert.assertFalse(Arrays.equals(f1.getCacheKey(), f2.getCacheKey()));
      Assert.assertArrayEquals(f1.getCacheKey(), f3.getCacheKey());

      NestedDataModule.registerHandlersAndSerde();
      f1 = new EqualityFilter("x", ColumnType.NESTED_DATA, ImmutableMap.of("x", ImmutableList.of(1, 2, 3)), null);
      f1_2 = new EqualityFilter("x", ColumnType.NESTED_DATA, ImmutableMap.of("x", ImmutableList.of(1, 2, 3)), null);
      f2 = new EqualityFilter("x", ColumnType.NESTED_DATA, ImmutableMap.of("x", ImmutableList.of(1, 2, 3, 4)), null);
      f3 = new EqualityFilter(
          "x",
          ColumnType.NESTED_DATA,
          ImmutableMap.of("x", ImmutableList.of(1, 2, 3)),
          new FilterTuning(true, null, null)
      );
      Assert.assertArrayEquals(f1.getCacheKey(), f1_2.getCacheKey());
      Assert.assertFalse(Arrays.equals(f1.getCacheKey(), f2.getCacheKey()));
      Assert.assertArrayEquals(f1.getCacheKey(), f3.getCacheKey());
    }

    @Test
    public void testInvalidParameters()
    {
      Throwable t = Assert.assertThrows(
          DruidException.class,
          () -> new EqualityFilter(null, ColumnType.STRING, null, null)
      );
      Assert.assertEquals("Invalid equality filter, column cannot be null", t.getMessage());
      t = Assert.assertThrows(
          DruidException.class,
          () -> new EqualityFilter("dim0", null, null, null)
      );
      Assert.assertEquals("Invalid equality filter on column [dim0], matchValueType cannot be null", t.getMessage());
      t = Assert.assertThrows(
          DruidException.class,
          () -> new EqualityFilter("dim0", ColumnType.STRING, null, null)
      );
      Assert.assertEquals("Invalid equality filter on column [dim0], matchValue cannot be null", t.getMessage());
    }

    @Test
    public void testGetDimensionRangeSet()
    {
      EqualityFilter filter = new EqualityFilter("x", ColumnType.STRING, "hello", null);

      RangeSet<String> set = TreeRangeSet.create();
      set.add(Range.singleton("hello"));
      Assert.assertEquals(set, filter.getDimensionRangeSet("x"));
      Assert.assertNull(filter.getDimensionRangeSet("y"));

      ExprEval<?> eval = ExprEval.ofType(ExpressionType.STRING_ARRAY, new Object[]{"abc", "def"});
      filter = new EqualityFilter("x", ColumnType.STRING_ARRAY, eval.value(), null);
      set = TreeRangeSet.create();
      set.add(Range.singleton(Arrays.deepToString(eval.asArray())));
      Assert.assertEquals(set, filter.getDimensionRangeSet("x"));
      Assert.assertNull(filter.getDimensionRangeSet("y"));
    }

    @Test
    public void test_equals()
    {
      EqualsVerifier.forClass(EqualityFilter.class).usingGetClass()
                    .withNonnullFields(
                        "column",
                        "matchValueType",
                        "matchValueEval",
                        "matchValue",
                        "predicateFactory",
                        "cachedOptimizedFilter"
                    )
                    .withPrefabValues(ColumnType.class, ColumnType.STRING, ColumnType.DOUBLE)
                    .withIgnoredFields("predicateFactory", "cachedOptimizedFilter", "matchValue")
                    .verify();
    }
  }
}
