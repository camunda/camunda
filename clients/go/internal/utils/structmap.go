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
	"fmt"
	"reflect"
	"strings"
	"unicode"
)

var stringerInterface = reflect.TypeOf((*fmt.Stringer)(nil)).Elem()

func MapMarshal(iface interface{}, tag string, omitempty, omitminus bool) map[string]interface{} {
	value := innerValue(reflect.ValueOf(iface))

	switch value.Kind() {
	case reflect.Struct:
		return MapStructMarshal(value, tag, omitempty, omitminus)
	case reflect.Map:
		return MapMapMarshal(value, tag, omitempty, omitminus)
	default:
		return nil
	}
}

func MapStructMarshal(value reflect.Value, tag string, omitempty, omitminus bool) map[string]interface{} {
	m := make(map[string]interface{})

	if !value.IsValid() {
		return m
	}

	typeof := value.Type()
	for i := 0; i < typeof.NumField(); i++ {
		fieldType := typeof.Field(i)
		if unicode.IsLower([]rune(fieldType.Name)[0]) {
			continue
		}

		tagvalue := fieldType.Tag.Get(tag)
		dostrconv := false
		omit := false
		name := ""
		if tagvalue != "" {
			omit = omitempty && strings.Contains(tagvalue, ",omitempty")
			commaidx := strings.IndexRune(tagvalue, ',')
			if commaidx < 0 {
				commaidx = len(tagvalue)
			}
			name = tagvalue[:commaidx]
			dostrconv = strings.Contains(tagvalue, ",string")
		}
		if name == "-" {
			if !omitminus {
				name = ""
			}
		}
		if name == "" && fieldType.Anonymous && innerType(fieldType.Type).Kind() == reflect.Struct && !dostrconv {
			imap := MapValueMarshal(value.Field(i), tag, omitempty, omitminus, false).(map[string]interface{})
			if len(imap) == 0 && omit {
				continue
			}
			for k, v := range imap {
				m[k] = v
			}
		} else {
			if name == "" {
				name = fieldType.Name
			}
			v := MapValueMarshal(value.Field(i), tag, omitempty, omitminus, dostrconv)
			if isEmptyValue(reflect.ValueOf(v)) && omit {
				continue
			}
			m[name] = v
		}
	}
	return m
}

func MapMapMarshal(value reflect.Value, tag string, omitempty, omitminus bool) map[string]interface{} {
	m := make(map[string]interface{})
	for _, k := range value.MapKeys() {
		name := toString(k)
		m[name] = MapValueMarshal(value.MapIndex(k), tag, omitempty, omitminus, false)
	}
	return m
}

func MapValueMarshal(value reflect.Value, tag string, omitempty, omitminus, dostrconv bool) (iface interface{}) {
	switch innerType(value.Type()).Kind() {
	case reflect.Struct:
		iface = MapStructMarshal(innerValue(value), tag, omitempty, omitminus)
	case reflect.Map:
		iface = MapMapMarshal(innerValue(value), tag, omitempty, omitminus)
	default:
		iface = value.Interface()
	}
	if dostrconv {
		iface = fmt.Sprint(innerValue(reflect.ValueOf(iface)).Interface())
	}
	return
}

func innerValue(value reflect.Value) reflect.Value {
	for value.Kind() == reflect.Ptr || value.Kind() == reflect.Interface {
		value = value.Elem()
	}
	return value
}

func innerType(value reflect.Type) reflect.Type {
	for value.Kind() == reflect.Ptr {
		value = value.Elem()
	}
	return value
}

func isEmptyValue(v reflect.Value) bool {
	switch v.Kind() {
	case reflect.Array, reflect.Map, reflect.Slice, reflect.String:
		return v.Len() == 0
	case reflect.Bool:
		return !v.Bool()
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		return v.Int() == 0
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64, reflect.Uintptr:
		return v.Uint() == 0
	case reflect.Float32, reflect.Float64:
		return v.Float() == 0
	case reflect.Interface, reflect.Ptr:
		return v.IsNil()
	}
	return false
}

func toString(value reflect.Value) (str string) {
	for {
		if value.Type().Implements(stringerInterface) {
			return value.Interface().(fmt.Stringer).String()
		}
		switch value.Kind() {
		case reflect.String:
			return value.String()
		case reflect.Array, reflect.Slice:
			elt := value.Type().Elem()
			var runes []rune
			switch elt.Kind() {
			case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
				for i := 0; i < value.Len(); i++ {
					r := rune(value.Index(i).Int())
					runes = append(runes, r)
				}
			case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
				for i := 0; i < value.Len(); i++ {
					r := rune(value.Index(i).Uint())
					runes = append(runes, r)
				}
			}
			return string(runes)
		case reflect.Ptr, reflect.Interface:
			value = value.Elem()
		case reflect.Struct:
			return ""
		case reflect.Bool, reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64,
			reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64, reflect.Uintptr,
			reflect.Float32, reflect.Float64, reflect.Complex64, reflect.Complex128, reflect.UnsafePointer:
			return fmt.Sprint(value.Interface())
		}
	}
}
