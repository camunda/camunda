package search

import (
	"fmt"
	"runtime"
	"sync"

	"camunda.com/helmunusedvalues/pkg/keys"
	"github.com/schollz/progressbar/v3"
)

// Determine the number of worker goroutines to use based on configuration
func (f *Finder) getWorkerCount() int {
	if f.Parallelism > 0 {
		// Use the explicitly configured value
		return f.Parallelism
	}

	// Auto-configure based on CPU cores
	numWorkers := runtime.NumCPU()
	if numWorkers < 2 {
		numWorkers = 2 // Minimum 2 workers
	} else if numWorkers > 8 {
		numWorkers = 8 // Cap at 8 workers to avoid too many concurrent processes
	}
	return numWorkers
}

// createProgressBar creates a configured progress bar for the analysis process
func createProgressBar(total int, description string) *progressbar.ProgressBar {
	return progressbar.NewOptions(total,
		progressbar.OptionEnableColorCodes(false),
		progressbar.OptionShowBytes(false),
		progressbar.OptionSetWidth(50),
		progressbar.OptionSetDescription(description),
		progressbar.OptionUseANSICodes(true),    // Use ANSI codes for better positioning
		progressbar.OptionSetPredictTime(false), // Don't show ETA
		progressbar.OptionSpinnerType(14),       // Use a dot spinner
	)
}

func (f *Finder) analyzeKeys(ks []string, showProgress bool) ([]keys.KeyUsage, error) {
	usages := make([]keys.KeyUsage, len(ks))

	usedKeysMap := make(map[string]bool)
	var usedKeysMutex sync.Mutex

	var bar *progressbar.ProgressBar
	if showProgress {
		bar = createProgressBar(len(ks), "Analyzing keys...")
	}

	numWorkers := f.getWorkerCount()

	results := f.processKeysInParallel(ks, usages, usedKeysMap, &usedKeysMutex, bar, showProgress, numWorkers)
	if showProgress {
		bar.Finish()
		fmt.Println()
	}

	if showProgress {
		bar.Finish()
	}

	return results, nil
}

type workItem struct {
	index int
	key   string
}

// Each key can appear directly within a template or indirectly through a pattern.
// Direct usage is something like {{ .Values.key }}.
// Indirect usage is something like {{- include "camundaPlatform.subChartImagePullSecrets" (dict "Values" (set (deepCopy .Values) "image" .Values.connectors.image)) }}
// See the test cases for examples of how to use this
func (f *Finder) processKeysInParallel(
	ks []string,
	usages []keys.KeyUsage,
	usedKeysMap map[string]bool,
	usedKeysMutex *sync.Mutex,
	bar *progressbar.ProgressBar,
	showProgress bool,
	numWorkers int,
) []keys.KeyUsage {
	jobs := make(chan workItem, len(ks))
	var wg sync.WaitGroup

	progressUpdates := make(chan int, len(ks))

	if showProgress {
		go func() {
			for range progressUpdates {
				bar.Add(1)
			}
		}()
	}

	for w := range numWorkers {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			for job := range jobs {
				// Skip empty keys
				if job.key == "" {
					if showProgress {
						progressUpdates <- 1
					}
					continue
				}

				usage := keys.KeyUsage{
					Key:       job.key,
					IsUsed:    false,
					UsageType: "unused",
				}

				isDirectlyUsed, locations := f.SearchForDirectUsageOfKeyAcrossAllTemplates(job.key)
				if isDirectlyUsed {
					usage.IsUsed = true
					usage.UsageType = "direct"
					usage.Locations = locations
					usedKeysMutex.Lock()
					usedKeysMap[job.key] = true
					usedKeysMutex.Unlock()
					usages[job.index] = usage
					if showProgress {
						progressUpdates <- 1
					}
					continue
				}

				for _, patternName := range f.Registry.Names {
					isUsed, parent, files := f.IsKeyUsedWithPattern(job.key, patternName)
					if isUsed {
						usage.IsUsed = true
						usage.UsageType = "pattern"
						usage.Locations = files
						usage.ParentKey = parent
						usage.PatternName = patternName
						usedKeysMutex.Lock()
						usedKeysMap[job.key] = true
						usedKeysMutex.Unlock()
						break
					}
				}

				usages[job.index] = usage
				if showProgress {
					progressUpdates <- 1
				}
			}
		}(w)
	}

	for i, key := range ks {
		if showProgress && i%10 == 0 {
			bar.Describe(fmt.Sprintf("Analyzing key: %s", key))
		}
		jobs <- workItem{index: i, key: key}
	}

	close(jobs)

	wg.Wait()

	if showProgress {
		close(progressUpdates)
	}

	return usages
}
