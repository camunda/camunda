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
	"github.com/stretchr/testify/suite"
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

type oauthCredsCacheTestSuite struct {
	*envSuite
}

func TestOAuthCredsProviderCacheSuite(t *testing.T) {
	suite.Run(t, &oauthCredsCacheTestSuite{new(envSuite)})
}

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

func (s *oauthCredsCacheTestSuite) TestReadCacheGoldenFile() {
	cachePath := copyCredentialsCacheGoldenFileToTempFile()
	cache, err := NewOAuthYamlCredentialsCache(cachePath)

	s.NoError(err)
	s.EqualValues(wombat, cache.Get(wombatAudience))
	s.EqualValues(aardvark, cache.Get(aardvarkAudience))
}

func (s *oauthCredsCacheTestSuite) TestWriteCacheGoldenFile() {
	capybaraAudience := "capybara.cloud.camunda.io"
	capybara := &OAuthCredentials{
		AccessToken: "capybara",
		ExpiresIn:   900,
		TokenType:   "Bearer",
		Scope:       "grpc",
	}
	cachePath := copyCredentialsCacheGoldenFileToTempFile()
	cache, err := NewOAuthYamlCredentialsCache(cachePath)
	s.NoError(err)

	err = cache.Update(capybaraAudience, capybara)
	s.NoError(err)

	cacheCopy, _ := NewOAuthYamlCredentialsCache(cachePath)
	err = cacheCopy.readCache()
	s.NoError(err)
	s.EqualValues(wombat, cacheCopy.Get(wombatAudience))
	s.EqualValues(aardvark, cacheCopy.Get(aardvarkAudience))
	s.EqualValues(capybara, cacheCopy.Get(capybaraAudience))
}

func (s *oauthCredsCacheTestSuite) TestOAuthYamlCredentialsCacheDefaultPath() {
	cache, err := NewOAuthYamlCredentialsCache("")
	s.NoError(err)
	s.Equal(DefaultOauthYamlCachePath, cache.path)
}

func (s *oauthCredsCacheTestSuite) TestOAuthYamlCredentialsCacheGetDefaultPath() {
	path := getDefaultOAuthYamlCredentialsCachePath()
	s.True(strings.HasSuffix(path, getDefaultOAuthYamlCredentialsCacheRelativePath()), "Expected %v to end with %v", path, getDefaultOAuthYamlCredentialsCacheRelativePath())
}

func (s *oauthCredsCacheTestSuite) TestOAuthYamlCredentialsCachePathFromEnvironment() {
	fakePath := copyCredentialsCacheGoldenFileToTempFile()
	file, err := ioutil.TempFile("", ".envCache")
	if err != nil {
		panic(err)
	}
	defer file.Close()

	env.set(OAuthCachePathEnvVar, file.Name())

	cache, err := NewOAuthYamlCredentialsCache(fakePath)
	s.NoError(err)
	s.Equal(file.Name(), cache.path)
	s.Empty(cache.audiences)
}

func copyCredentialsCacheGoldenFileToTempFile() string {
	cache, err := ioutil.ReadFile("testdata/credentialsCache.yml")
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
