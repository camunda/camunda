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
	"fmt"
	"github.com/mitchellh/go-homedir"
	"golang.org/x/oauth2"
	"gopkg.in/yaml.v2"
	"log"
	"os"
	"path"
	"path/filepath"
	"sync"
)

const OAuthCachePathEnvVar = "ZEEBE_CLIENT_CONFIG_PATH"
const DefaultOAuthCacheFileDir = ".camunda"
const DefaultOAuthCacheFile = "credentials"
const oauthYamlCredentialsCachePerm = 0660

const ErrOAuthCredentialsCacheFolderIsNotDir = Error("OAuth credentials cache folder is not a directory, cannot create cache file under it")
const ErrOAuthCredentialsCacheIsDir = Error("OAuth credentials cache must be a file, not a directory")

var DefaultOauthYamlCachePath = getDefaultOAuthYamlCredentialsCachePath()

// OAuthCredentialsCache is used to cache results of fetching OAuth credentials
type OAuthCredentialsCache interface {
	// Refresh should clear and re-populate the cache from defaults
	Refresh() error
	// Get should return the cached credentials for the given audience, or nil
	Get(audience string) *oauth2.Token
	// Update should set the credentials as the cached credentials for the given audience
	Update(audience string, token *oauth2.Token) error
}

type oauthYamlCredentialsCache struct {
	path      string
	audiences map[string]*oauthCachedCredentials
	lock      sync.RWMutex
}

type oauthCachedCredentials struct {
	Auth struct{ Credentials *oauth2.Token }
}

func NewOAuthYamlCredentialsCache(path string) (OAuthCredentialsCache, error) {
	var err error

	envCachePath := env.get(OAuthCachePathEnvVar)
	if envCachePath != "" {
		path = envCachePath
	} else if path == "" {
		path = DefaultOauthYamlCachePath
	}

	if err = ensureOAuthCacheFileExists(path); err != nil {
		return nil, err
	}

	cache := oauthYamlCredentialsCache{
		path:      path,
		audiences: make(map[string]*oauthCachedCredentials),
	}

	if err := cache.readCache(); err != nil {
		return nil, err
	}

	return &cache, nil
}

// Refresh overwrites the current in-memory contents with whatever is written in the cache file
func (cache *oauthYamlCredentialsCache) Refresh() error {
	return cache.readCache()
}

// Get returns the cached credentials for the given audience or nil
// Note: it does not read the cache again
func (cache *oauthYamlCredentialsCache) Get(audience string) *oauth2.Token {
	cache.lock.RLock()
	defer cache.lock.RUnlock()

	cachedCredentials := cache.audiences[audience]
	if cachedCredentials == nil {
		return nil
	}

	return cachedCredentials.Auth.Credentials
}

// Update updates the in-memory mapping for the given audience and credentials, and flushes its contents to disk
func (cache *oauthYamlCredentialsCache) Update(audience string, credentials *oauth2.Token) error {
	cache.put(audience, credentials)
	return cache.writeCache()
}

func (cache *oauthYamlCredentialsCache) put(audience string, credentials *oauth2.Token) {
	cache.lock.Lock()
	defer cache.lock.Unlock()
	cache.audiences[audience] = &oauthCachedCredentials{
		Auth: struct{ Credentials *oauth2.Token }{Credentials: credentials},
	}
}

// readCache will overwrite the current contents of cache.audiences, so use carefully
func (cache *oauthYamlCredentialsCache) readCache() error {
	cacheContents, err := os.ReadFile(cache.path)
	if err != nil {
		return err
	}

	cache.lock.Lock()
	defer cache.lock.Unlock()

	err = yaml.Unmarshal(cacheContents, &cache.audiences)
	return err
}

// writeCache will overwrite any contents in the current cache file, so use carefully
func (cache *oauthYamlCredentialsCache) writeCache() error {
	cache.lock.RLock()
	defer cache.lock.RUnlock()

	cacheContents, err := yaml.Marshal(&cache.audiences)
	if err != nil {
		return err
	}

	return os.WriteFile(cache.path, cacheContents, 0600)
}

func getDefaultOAuthYamlCredentialsCacheRelativePath() string {
	return path.Join(DefaultOAuthCacheFileDir, DefaultOAuthCacheFile)
}

func getDefaultOAuthYamlCredentialsCachePath() string {
	homeDir, err := homedir.Dir()
	if err == nil {
		homeDir, err = homedir.Expand(homeDir)
	}

	if err != nil {
		log.Printf("Failed to read default home directory: %s", err.Error())
	}

	return path.Join(homeDir, getDefaultOAuthYamlCredentialsCacheRelativePath())
}

// ensures the cache file exists by creating it if need be, and fails if it already exists and is a directory
func ensureOAuthCacheFileExists(path string) (err error) {
	info, err := os.Stat(path)
	if err != nil {
		if os.IsNotExist(err) {
			if err = ensureOAuthCachePathSegmentsExist(filepath.Dir(path)); err != nil {
				return err
			}

			if file, creationError := os.OpenFile(path, os.O_RDWR|os.O_CREATE|os.O_TRUNC, oauthYamlCredentialsCachePerm); creationError == nil {
				err = file.Close()
			} else {
				err = creationError
			}
		}
	} else if info.IsDir() {
		err = fmt.Errorf("%s: %w", path, ErrOAuthCredentialsCacheIsDir)
	}

	return
}

func ensureOAuthCachePathSegmentsExist(directory string) error {
	dirInfo, err := os.Stat(directory)
	if err != nil {
		if !os.IsNotExist(err) {
			return err
		}

		if err = os.MkdirAll(directory, 0770); err != nil {
			return err
		}
	} else if !dirInfo.IsDir() {
		return fmt.Errorf("%s: %w", directory, ErrOAuthCredentialsCacheFolderIsNotDir)
	}

	return err
}
