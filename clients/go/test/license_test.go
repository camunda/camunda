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

package test

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const license = `// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
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
`

var skipList = []string{
	// These files are generated.
	"../pkg/pb/gateway.pb.go",
	"../internal/mock_pb/",
	"../vendor/",
}

func TestLicense(t *testing.T) {
	err := filepath.Walk("..", func(path string, fi os.FileInfo, err error) error {
		for _, skip := range skipList {
			if strings.HasPrefix(path, skip) {
				return nil
			}
		}

		if err != nil {
			return err
		}

		if filepath.Ext(path) != ".go" {
			return nil
		}

		src, err := os.ReadFile(path)
		if err != nil {
			return nil
		}

		// Also check it is at the top of the file.
		if !bytes.HasPrefix(src, []byte(license)) {
			t.Errorf("%v: license header not present", path)
		}
		return nil
	})

	if err != nil {
		t.Fatal(err)
	}
}
