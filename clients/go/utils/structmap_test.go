// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package utils

import (
	"bytes"
	"reflect"
	"testing"
)

type nonStringerStruct struct {
}

type stringerStruct struct {
}

func (s *stringerStruct) String() string {
	return "somestring"
}

func TestToString_Stringer(t *testing.T) {
	str := &stringerStruct{}
	if r := toString(reflect.ValueOf(str)); r != "somestring" {
		t.Fatal("stringer fail")
	}

	nonstr := &nonStringerStruct{}
	if r := toString(reflect.ValueOf(nonstr)); r != "" {
		t.Fatal("nonstringer fail", r)
	}
}

func TestToString_Indirect(t *testing.T) {
	str := &stringerStruct{}
	if r := toString(reflect.ValueOf(&str)); r != "somestring" {
		t.Fatal("indirect stringer fail")
	}
}

func TestToString_IndirectReverse(t *testing.T) {
	str := stringerStruct{}
	if r := toString(reflect.ValueOf(str)); r != "" {
		t.Fatal("indirect reverse stringer fail")
	}
}

func TestToString_Slice(t *testing.T) {
	str := []rune{'t', 'e', 's', 't'}
	if r := toString(reflect.ValueOf(str)); r != "test" {
		t.Fatal("slice fail")
	}
}

func TestToString_Array(t *testing.T) {
	str := [4]rune{'t', 'e', 's', 't'}
	if r := toString(reflect.ValueOf(str)); r != "test" {
		t.Fatal("array fail")
	}
}

func TestMapValueMarshal_Scalars(t *testing.T) {
	if MapValueMarshal(reflect.ValueOf(int(-1<<63)), "json", true, true, false).(int) != -1<<63 {
		t.Fatal("int mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(int8(-1<<7)), "json", true, true, false).(int8) != -1<<7 {
		t.Fatal("int8 mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(int16(-1<<15)), "json", true, true, false).(int16) != -1<<15 {
		t.Fatal("int16 mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(int32(-1<<31)), "json", true, true, false).(int32) != -1<<31 {
		t.Fatal("int32 mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(int64(-1<<63)), "json", true, true, false).(int64) != -1<<63 {
		t.Fatal("int64 mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(uint(1<<64-1)), "json", true, true, false).(uint) != 1<<64-1 {
		t.Fatal("uint mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(uint8(1<<8-1)), "json", true, true, false).(uint8) != 1<<8-1 {
		t.Fatal("uint8 mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(uint16(1<<16-1)), "json", true, true, false).(uint16) != 1<<16-1 {
		t.Fatal("uint16 mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(uint32(1<<32-1)), "json", true, true, false).(uint32) != 1<<32-1 {
		t.Fatal("uint32 mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(uint64(1<<64-1)), "json", true, true, false).(uint64) != 1<<64-1 {
		t.Fatal("uint64 mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(float32(3.4E+38)), "json", true, true, false).(float32) != 3.4E+38 {
		t.Fatal("float32 mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(float64(1.7E+308)), "json", true, true, false).(float64) != 1.7E+308 {
		t.Fatal("float64 mismatch")
	}
	if MapValueMarshal(reflect.ValueOf("test"), "json", true, true, false).(string) != "test" {
		t.Fatal("string mismatch")
	}
	if MapValueMarshal(reflect.ValueOf(true), "json", true, true, false).(bool) != true {
		t.Fatal("bool mismatch")
	}
	if slice := MapValueMarshal(reflect.ValueOf([]byte{1, 2, 3}), "json", true, true, false).([]byte); !bytes.Equal(slice, []byte{1, 2, 3}) {
		t.Fatal("slice mismatch")
	}
	if arr := MapValueMarshal(reflect.ValueOf([3]byte{1, 2, 3}), "json", true, true, false).([3]byte); !bytes.Equal(arr[:], []byte{1, 2, 3}) {
		t.Fatal("type mismatch")
	}
	i := 3
	if MapValueMarshal(reflect.ValueOf(&i), "json", true, true, false).(*int) != &i {
		t.Fatal("ptr mismatch")
	}

	resmap := MapValueMarshal(reflect.ValueOf(map[string]interface{}{
		"a": 123,
		"b": true,
		"c": map[string]interface{}{
			"foo": "bar",
		},
	}), "json", true, true, false).(map[string]interface{})

	if resmap["a"].(int) != 123 {
		t.Fatal("map int fail")
	}
	if resmap["b"].(bool) != true {
		t.Fatal("map bool fail")
	}
	if resmap["c"].(map[string]interface{})["foo"].(string) != "bar" {
		t.Fatal("map nested map string fail")
	}

	resstruct := MapStructMarshal(reflect.ValueOf(struct {
		A int  `json:"a"`
		B bool `json:"b"`
		C struct {
			Foo string `json:"foo"`
		} `json:"c"`
	}{
		A: 123,
		B: true,
		C: struct {
			Foo string `json:"foo"`
		}{Foo: "bar"},
	}), "json", true, true)

	if resstruct["a"].(int) != 123 {
		t.Fatal("map int fail")
	}
	if resstruct["b"].(bool) != true {
		t.Fatal("map bool fail")
	}
	if resstruct["c"].(map[string]interface{})["foo"].(string) != "bar" {
		t.Fatal("map nested map string fail")
	}
}

func TestMapValueMarshal_ScalarsStrings(t *testing.T) {
	if MapValueMarshal(reflect.ValueOf(int(-1<<63)), "json", true, true, true).(string) != "-9223372036854775808" {
		t.Fatal("int string fail")
	}
	if MapValueMarshal(reflect.ValueOf(int8(-1<<7)), "json", true, true, true).(string) != "-128" {
		t.Fatal("int8 string fail")
	}
	if MapValueMarshal(reflect.ValueOf(int16(-1<<15)), "json", true, true, true).(string) != "-32768" {
		t.Fatal("int16 string fail")
	}
	if MapValueMarshal(reflect.ValueOf(int32(-1<<31)), "json", true, true, true).(string) != "-2147483648" {
		t.Fatal("int32 string fail")
	}
	if MapValueMarshal(reflect.ValueOf(int64(-1<<63)), "json", true, true, true).(string) != "-9223372036854775808" {
		t.Fatal("int64 string fail")
	}
	if MapValueMarshal(reflect.ValueOf(uint(1<<64-1)), "json", true, true, true).(string) != "18446744073709551615" {
		t.Fatal("uint string fail")
	}
	if MapValueMarshal(reflect.ValueOf(uint8(1<<8-1)), "json", true, true, true).(string) != "255" {
		t.Fatal("uint8 string fail")
	}
	if MapValueMarshal(reflect.ValueOf(uint16(1<<16-1)), "json", true, true, true).(string) != "65535" {
		t.Fatal("uint16 string fail")
	}
	if MapValueMarshal(reflect.ValueOf(uint32(1<<32-1)), "json", true, true, true).(string) != "4294967295" {
		t.Fatal("uint32 string fail")
	}
	if MapValueMarshal(reflect.ValueOf(uint64(1<<64-1)), "json", true, true, true).(string) != "18446744073709551615" {
		t.Fatal("uint64 string fail")
	}
	if MapValueMarshal(reflect.ValueOf(float32(3.4e+38)), "json", true, true, true).(string) != "3.4e+38" {
		t.Fatal("float32 string fail")
	}
	if MapValueMarshal(reflect.ValueOf(float64(1.7e+308)), "json", true, true, true).(string) != "1.7e+308" {
		t.Fatal("float64 string fail")
	}
	if MapValueMarshal(reflect.ValueOf("test"), "json", true, true, true).(string) != "test" {
		t.Fatal("string string fail")
	}
	if MapValueMarshal(reflect.ValueOf(true), "json", true, true, true).(string) != "true" {
		t.Fatal("bool string fail")
	}
	if MapValueMarshal(reflect.ValueOf([]byte{1, 2, 3}), "json", true, true, true).(string) != "[1 2 3]" {
		t.Fatal("slice string fail")
	}
	if MapValueMarshal(reflect.ValueOf([3]byte{1, 2, 3}), "json", true, true, true).(string) != "[1 2 3]" {
		t.Fatal("array string fail")
	}
	i := 3
	if MapValueMarshal(reflect.ValueOf(&i), "json", true, true, true).(string) != "3" {
		t.Fatal("ptr string mismatch", MapValueMarshal(reflect.ValueOf(&i), "json", true, true, true))
	}
}

func TestMapMapMarshal(t *testing.T) {
	defer func() {
		if err := recover(); err != nil {
			t.Fatal(err)
		}
	}()
	res := MapMapMarshal(reflect.ValueOf(map[string]interface{}{
		"a": 123,
		"b": true,
		"c": map[string]interface{}{
			"foo": "bar",
		},
	}), "json", true, true)

	if res["a"].(int) != 123 {
		t.Fatal("map int fail")
	}
	if res["b"].(bool) != true {
		t.Fatal("map bool fail")
	}
	if res["c"].(map[string]interface{})["foo"].(string) != "bar" {
		t.Fatal("map nested map string fail")
	}
}

func TestMapMapMarshal_IntKey(t *testing.T) {
	defer func() {
		if err := recover(); err != nil {
			t.Fatal(err)
		}
	}()
	res := MapMapMarshal(reflect.ValueOf(map[int]interface{}{
		1: 123,
		2: true,
		3: map[string]interface{}{
			"foo": "bar",
		},
	}), "json", true, true)

	if res["1"].(int) != 123 {
		t.Fatal("map int fail")
	}
	if res["2"].(bool) != true {
		t.Fatal("map bool fail")
	}
	if res["3"].(map[string]interface{})["foo"].(string) != "bar" {
		t.Fatal("map nested map string fail")
	}
}

func TestMapStructMarshal(t *testing.T) {
	res := MapStructMarshal(reflect.ValueOf(struct {
		A int
		B bool
		C struct {
			Foo string
		}
	}{
		A: 123,
		B: true,
		C: struct{ Foo string }{Foo: "bar"},
	}), "json", true, true)

	if res["A"].(int) != 123 {
		t.Fatal("map int fail")
	}
	if res["B"].(bool) != true {
		t.Fatal("map bool fail")
	}
	if res["C"].(map[string]interface{})["Foo"].(string) != "bar" {
		t.Fatal("map nested map string fail")
	}
}

func TestMapStructMarshal_Tagged(t *testing.T) {
	res := MapStructMarshal(reflect.ValueOf(struct {
		A int  `json:"a"`
		B bool `json:"b"`
		C struct {
			Foo string `json:"foo"`
		} `json:"c"`
	}{
		A: 123,
		B: true,
		C: struct {
			Foo string `json:"foo"`
		}{Foo: "bar"},
	}), "json", true, true)

	if res["a"].(int) != 123 {
		t.Fatal("map int fail")
	}
	if res["b"].(bool) != true {
		t.Fatal("map bool fail")
	}
	if res["c"].(map[string]interface{})["foo"].(string) != "bar" {
		t.Fatal("map nested map string fail")
	}
}

func TestMapStructMarshal_TaggedOmitOmit(t *testing.T) {
	res := MapStructMarshal(reflect.ValueOf(struct {
		A int  `json:"a,omitempty"`
		B bool `json:"b,omitempty"`
		C struct {
			Foo string `json:"foo,omitempty"`
		} `json:"c"`
	}{
		A: 0,
		B: false,
		C: struct {
			Foo string `json:"foo,omitempty"`
		}{Foo: ""},
	}), "json", true, true)

	if _, ok := res["a"]; ok {
		t.Fatal("map int should be missing")
	}
	if _, ok := res["b"]; ok {
		t.Fatal("map bool should be missing")
	}
	if _, ok := res["c"]; !ok {
		t.Fatal("map int should be present")
	}
	if _, ok := res["c"].(map[string]interface{})["foo"]; ok {
		t.Fatal("map nested map string should be missing")
	}
}

func TestMapStructMarshal_TaggedOmitForce(t *testing.T) {
	res := MapStructMarshal(reflect.ValueOf(struct {
		A int  `json:"a,omitempty"`
		B bool `json:"b,omitempty"`
		C struct {
			Foo string `json:"foo,omitempty"`
		} `json:"c"`
	}{
		A: 0,
		B: false,
		C: struct {
			Foo string `json:"foo,omitempty"`
		}{Foo: ""},
	}), "json", false, true)

	if res["a"].(int) != 0 {
		t.Fatal("map int fail")
	}
	if res["b"].(bool) != false {
		t.Fatal("map bool fail")
	}
	if res["c"].(map[string]interface{})["foo"].(string) != "" {
		t.Fatal("map nested map string fail")
	}
}

func TestMapStructMarshal_TaggedMinusOmit(t *testing.T) {
	res := MapStructMarshal(reflect.ValueOf(struct {
		A int  `json:"-"`
		B bool `json:"-"`
		C struct {
			Foo string `json:"-"`
		} `json:"c"`
	}{
		A: 123,
		B: true,
		C: struct {
			Foo string `json:"-"`
		}{Foo: "bar"},
	}), "json", true, true)

	if _, ok := res["a"]; ok {
		t.Fatal("map int should be missing")
	}
	if _, ok := res["b"]; ok {
		t.Fatal("map bool should be missing")
	}
	if _, ok := res["c"]; !ok {
		t.Fatal("map int should be present")
	}
	if _, ok := res["c"].(map[string]interface{})["foo"]; ok {
		t.Fatal("map nested map string should be missing")
	}
}

func TestMapStructMarshal_TaggedMinusForce(t *testing.T) {
	res := MapStructMarshal(reflect.ValueOf(struct {
		A int  `json:"-"`
		B bool `json:"-"`
		C struct {
			Foo string `json:"-"`
		} `json:"c"`
	}{
		A: 123,
		B: true,
		C: struct {
			Foo string `json:"-"`
		}{Foo: "bar"},
	}), "json", true, false)

	if res["A"].(int) != 123 {
		t.Fatal("map int fail")
	}
	if res["B"].(bool) != true {
		t.Fatal("map bool fail")
	}
	if res["c"].(map[string]interface{})["Foo"].(string) != "bar" {
		t.Fatal("map nested map string fail")
	}
}

func TestMapMarshal(t *testing.T) {
	res := MapMarshal(struct {
		A int  `json:"a,omitempty"`
		B bool `json:"b,omitempty"`
		C struct {
			Foo string `json:"foo,omitempty"`
		} `json:"c,omitempty"`
	}{
		A: 0,
		B: false,
		C: struct {
			Foo string `json:"foo,omitempty"`
		}{Foo: ""},
	}, "json", false, true)

	if res["a"].(int) != 0 {
		t.Fatal("map int fail")
	}
	if res["b"].(bool) != false {
		t.Fatal("map bool fail")
	}
	if res["c"].(map[string]interface{})["foo"].(string) != "" {
		t.Fatal("map nested map string fail")
	}
}

func TestMapMarshal_EmbeddedUntagged(t *testing.T) {
	type InnerStruct struct {
		Bar string `json:"bar"`
	}

	type OuterStruct struct {
		InnerStruct
		Foo string `json:"foo"`
	}

	res := MapMarshal(OuterStruct{
		Foo: "foo",
		InnerStruct: InnerStruct{
			Bar: "bar",
		},
	}, "json", true, true)

	if v, ok := res["foo"]; !ok || v != "foo" {
		t.Fatal("foo member is missing or invalid")
	}
	if v, ok := res["bar"]; !ok || v != "bar" {
		t.Fatal("bar member of embedded struct is missing or invalid")
	}
}

func TestMapMarshal_EmbeddedTagged(t *testing.T) {
	type InnerStruct struct {
		Bar string `json:"bar"`
	}

	type OuterStruct struct {
		InnerStruct `json:"inner"`
		Foo         string `json:"foo"`
	}

	res := MapMarshal(OuterStruct{
		Foo: "foo",
		InnerStruct: InnerStruct{
			Bar: "bar",
		},
	}, "json", true, true)

	if v, ok := res["foo"]; !ok || v != "foo" {
		t.Fatal("foo member is missing or invalid")
	}
	if v, ok := res["inner"]; !ok {
		t.Fatal("inner struct is missing")
	} else {
		if v, ok := v.(map[string]interface{})["bar"]; !ok || v != "bar" {
			t.Fatal("bar member of inner struct is missing or invalid")
		}
	}
}
