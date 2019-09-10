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
package zbc

import (
	"github.com/stretchr/testify/require"
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const wombatAudience = "wombat.cloud.camunda.io"

var wombat = &OAuthCredentials{
	AccessToken: "wombat",
	ExpiresIn:   3600,
	TokenType:   "Bearer",
	Scope:       "grpc",
}

const aardvarkAudience = "aardvark.cloud.camunda.io"

var aardvark = &OAuthCredentials{
	AccessToken: "aardvark",
	ExpiresIn:   1800,
	TokenType:   "Bearer",
	Scope:       "grpc",
}

func init() {
	// overwrite the default cache path for testing
	file, err := ioutil.TempFile("", "credentialsCache.tmp.yml")
	if err != nil {
		panic(err)
	}
	defer file.Close()

	DefaultOauthYamlCachePath, err = filepath.Abs(file.Name())
	if err != nil {
		panic(err)
	}
}

func TestReadCacheGoldenFile(t *testing.T) {
	cachePath := copyCredentialsCacheGoldenFileToTempFile()
	cache, err := NewOAuthYamlCredentialsCache(cachePath)

	require.NoError(t, err)
	require.EqualValues(t, wombat, cache.Get(wombatAudience))
	require.EqualValues(t, aardvark, cache.Get(aardvarkAudience))
}

func TestWriteCacheGoldenFile(t *testing.T) {
	capybaraAudience := "capybara.cloud.camunda.io"
	capybara := &OAuthCredentials{
		AccessToken: "capybara",
		ExpiresIn:   900,
		TokenType:   "Bearer",
		Scope:       "grpc",
	}
	cachePath := copyCredentialsCacheGoldenFileToTempFile()
	cache, err := NewOAuthYamlCredentialsCache(cachePath)
	require.NoError(t, err)

	err = cache.Update(capybaraAudience, capybara)
	require.NoError(t, err)

	cacheCopy, _ := NewOAuthYamlCredentialsCache(cachePath)
	err = cacheCopy.readCache()
	require.NoError(t, err)
	require.EqualValues(t, wombat, cacheCopy.Get(wombatAudience))
	require.EqualValues(t, aardvark, cacheCopy.Get(aardvarkAudience))
	require.EqualValues(t, capybara, cacheCopy.Get(capybaraAudience))
}

func TestOAuthYamlCredentialsCacheDefaultPath(t *testing.T) {
	cache, err := NewOAuthYamlCredentialsCache("")
	require.NoError(t, err)
	require.Equal(t, DefaultOauthYamlCachePath, cache.path)
}

func TestOAuthYamlCredentialsCacheGetDefaultPath(t *testing.T) {
	path := getDefaultOAuthYamlCredentialsCachePath()
	require.True(t, strings.HasSuffix(path, getDefaultOAuthYamlCredentialsCacheRelativePath()), "Expected %v to end with %v", path, getDefaultOAuthYamlCredentialsCacheRelativePath())
}

func TestOAuthYamlCredentialsCachePathFromEnvironment(t *testing.T) {
	fakePath := copyCredentialsCacheGoldenFileToTempFile()
	file, err := ioutil.TempFile("", ".envCache")
	if err != nil {
		panic(err)
	}
	defer file.Close()

	err = os.Setenv(OAuthCachePathEnvVar, file.Name())
	defer os.Unsetenv(OAuthCachePathEnvVar)
	cache, err := NewOAuthYamlCredentialsCache(fakePath)
	require.NoError(t, err)
	require.Equal(t, file.Name(), cache.path)
	require.Empty(t, cache.audiences)
}

func copyCredentialsCacheGoldenFileToTempFile() string {
	cache, err := ioutil.ReadFile("../resources/credentialsCache.yml")
	if err != nil {
		panic(err)
	}

	file, err := ioutil.TempFile("", ".credentialsCache")
	if err != nil {
		panic(err)
	}

	err = file.Close()
	if err != nil {
		panic(err)
	}

	path, err := filepath.Abs(file.Name())
	if err != nil {
		panic(err)
	}

	err = ioutil.WriteFile(path, cache, 0644)
	if err != nil {
		panic(err)
	}

	return path
}

// helper to truncate the default cache file
func truncateDefaultOAuthYamlCacheFile() {
	err := os.Remove(DefaultOauthYamlCachePath)
	if err != nil {
		if !os.IsNotExist(err) {
			panic(err)
		}
	}
}
