package main

import (
	"io"
        "fmt"
	"net/http"
	"os"
        "archive/tar"
        "archive/zip"
        "compress/gzip"
        "path/filepath"
        "runtime"
        "strings"
        "errors"
)

func downloadFile(filepath string, url string) error {
	out, err := os.Create(filepath)
	if err != nil {
		return err
	}
	defer out.Close()

	resp, err := http.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("bad status: %s", resp.Status)
	}

	_, err = io.Copy(out, resp.Body)
	if err != nil {
		return err
	}

	return nil
}

func createTarGzArchive(files []string, buf io.Writer) error {
	gw := gzip.NewWriter(buf)
	defer gw.Close()
	tw := tar.NewWriter(gw)
	defer tw.Close()

	for _, file := range files {
		err := addToArchive(tw, file)
		if err != nil {
			return err
		}
	}

	return nil
}

func extractTarGzArchive(filename string, xpath string) error {
        tarFile, err := os.Open(filename)
        if err != nil {
                panic(err)
        }
        defer tarFile.Close()

        gz, err := gzip.NewReader(tarFile)
        if err != nil {
                panic(err)
        }
        defer gz.Close()

        absPath, err := filepath.Abs(xpath)

        _, err = os.Stat(absPath)
        if errors.Is(err, os.ErrNotExist) {
                os.Mkdir(absPath, 0755)
        }

        tr := tar.NewReader(gz)
        for {
                hdr, err := tr.Next()
                if err == io.EOF {
                        break
                }
                if err != nil {
                        panic(err)
                }
                finfo := hdr.FileInfo()
                fileName := hdr.Name
                absFileName := filepath.Join(absPath, fileName)

                if finfo.Mode().IsDir() {
                        if err := os.MkdirAll(absFileName, 0755); err != nil {
                                panic(err)
                        }
                        continue
                } else {
                        parent := filepath.Dir(absFileName)
                        _, err = os.Stat(parent)
                        if errors.Is(err, os.ErrNotExist) {
                                if err := os.MkdirAll(parent, 0755); err != nil {
                                        panic(err)
                                }
                        }
                }
                file, err := os.OpenFile(
                        absFileName,
                        os.O_RDWR|os.O_CREATE|os.O_TRUNC,
                        finfo.Mode().Perm(),
                )
                if err != nil {
                        panic(err)
                }
                fmt.Printf("x %s\n", absFileName)
                n, cpErr := io.Copy(file, tr)
                if closeErr := file.Close(); closeErr != nil {
                        panic(err)
                }
                if cpErr != nil {
                        panic(cpErr)
                }
                if n != finfo.Size() {
                        return fmt.Errorf("wrote %d, want %d", n, finfo.Size())
                }
        }
        return nil
}




func addToArchive(tw *tar.Writer, filename string) error {
	file, err := os.Open(filename)
	if err != nil {
		return err
	}
	defer file.Close()

	info, err := file.Stat()
	if err != nil {
		return err
	}

	header, err := tar.FileInfoHeader(info, info.Name())
	if err != nil {
		return err
	}

	header.Name = filename

	err = tw.WriteHeader(header)
	if err != nil {
		return err
	}

	_, err = io.Copy(tw, file)
	if err != nil {
		return err
	}

	return nil
}

func zipSource(source, target string) error {
    f, err := os.Create(target)
    if err != nil {
        return err
    }
    defer f.Close()

    writer := zip.NewWriter(f)
    defer writer.Close()

    return filepath.Walk(source, func(path string, info os.FileInfo, err error) error {
        if err != nil {
            return err
        }

        header, err := zip.FileInfoHeader(info)
        if err != nil {
            return err
        }

        header.Method = zip.Deflate

        header.Name, err = filepath.Rel(filepath.Dir(source), path)
        if err != nil {
            return err
        }
        if info.IsDir() {
            header.Name += "/"
        }

        headerWriter, err := writer.CreateHeader(header)
        if err != nil {
            return err
        }

        if info.IsDir() {
            return nil
        }

        f, err := os.Open(path)
        if err != nil {
            return err
        }
        defer f.Close()

        _, err = io.Copy(headerWriter, f)
        return err
    })
}


func unzipSource(source, destination string) error {
    reader, err := zip.OpenReader(source)
    if err != nil {
        return err
    }
    defer reader.Close()

    destination, err = filepath.Abs(destination)
    if err != nil {
        return err
    }

    for _, f := range reader.File {
        err := unzipFile(f, destination)
        if err != nil {
            return err
        }
    }

    return nil
}

func unzipFile(f *zip.File, destination string) error {
    filePath := filepath.Join(destination, f.Name)
    if !strings.HasPrefix(filePath, filepath.Clean(destination)+string(os.PathSeparator)) {
        return fmt.Errorf("invalid file path: %s", filePath)
    }

    if f.FileInfo().IsDir() {
        if err := os.MkdirAll(filePath, os.ModePerm); err != nil {
            return err
        }
        return nil
    }

    if err := os.MkdirAll(filepath.Dir(filePath), os.ModePerm); err != nil {
        return err
    }

    destinationFile, err := os.OpenFile(filePath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, f.Mode())
    if err != nil {
        return err
    }
    defer destinationFile.Close()

    zippedFile, err := f.Open()
    if err != nil {
        return err
    }
    defer zippedFile.Close()

    if _, err := io.Copy(destinationFile, zippedFile); err != nil {
        return err
    }
    return nil
}

func Clean(camundaVersion string, elasticsearchVersion string) {
        os.RemoveAll("elasticsearch-" + elasticsearchVersion)
        os.RemoveAll("camunda-zeebe-" + camundaVersion)

        _, err := os.Stat(filepath.Join("log", "camunda.log"))
        if errors.Is(err, os.ErrNotExist) {
                os.Remove(filepath.Join("log", "camunda.log"))
        }
        _, err = os.Stat(filepath.Join("log", "connectors.log"))
        if errors.Is(err, os.ErrNotExist) {
                os.Remove(filepath.Join("log", "connectors.log"))
        }
        _, err = os.Stat(filepath.Join("log", "elasticsearch.log"))
        if errors.Is(err, os.ErrNotExist) {
                os.Remove(filepath.Join("log", "elasticsearch.log"))
        }
}


func PackageWindows(camundaVersion string, elasticsearchVersion string) {
        elasticsearchUrl := "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-" + elasticsearchVersion + "-windows-x86_64.zip"
        elasticsearchFilePath := "elasticsearch-" + elasticsearchVersion + ".zip"
        Clean(camundaVersion, elasticsearchVersion)


        _, err := os.Stat(filepath.Join(elasticsearchFilePath))
        if errors.Is(err, os.ErrNotExist) {
                err = downloadFile(elasticsearchFilePath, elasticsearchUrl)
                if err != nil {
                        panic(err)
                }

                fmt.Println("File downloaded successfully to " + elasticsearchFilePath)
        }

        elasticsearchExtractedDir := "elasticsearch-" + elasticsearchVersion
        _, err = os.Stat(elasticsearchExtractedDir)
        if errors.Is(err, os.ErrNotExist) {
                err = unzipSource(elasticsearchFilePath, elasticsearchExtractedDir)
                if err != nil {
                        panic(err)
                }
        }

        camundaFilePath := "camunda-zeebe-" + camundaVersion + ".zip"
        camundaUrl := "https://github.com/camunda/camunda/releases/download/" + camundaVersion + "/" + camundaFilePath

        _, err = os.Stat(filepath.Join(camundaFilePath))
        if errors.Is(err, os.ErrNotExist) {
                err = downloadFile(camundaFilePath, camundaUrl)
                if err != nil {
                        panic(err)
                }

                fmt.Println("File downloaded successfully to " + camundaFilePath)
        }
        camundaExtractedDir := "camunda-zeebe-" + camundaVersion
        _, err = os.Stat(camundaExtractedDir)
        if errors.Is(err, os.ErrNotExist) {
                err = unzipSource(camundaFilePath, camundaExtractedDir)
                if err != nil {
                        panic(err)
                }
        }


        connectorsFilePath := "connector-runtime-bundle-" + camundaVersion + "-with-dependencies.jar"
        connectorsUrl := "https://repo1.maven.org/maven2/io/camunda/connector/connector-runtime-bundle/" + camundaVersion + "/" + connectorsFilePath

        _, err = os.Stat(filepath.Join(camundaFilePath))
        if errors.Is(err, os.ErrNotExist) {
                err = downloadFile(connectorsFilePath, connectorsUrl)
                if err != nil {
                        panic(err)
                }

                fmt.Println("File downloaded successfully to " + connectorsFilePath)
        }
}


func PackageUnix(camundaVersion string, elasticsearchVersion string) {

        var architecture string
        if runtime.GOARCH == "amd64" {
                architecture = "x86_64"
        } else if runtime.GOARCH == "arm64" {
                architecture = "aarch64"
        }
        Clean(camundaVersion, elasticsearchVersion)

        elasticsearchUrl := "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-" + elasticsearchVersion + "-" + runtime.GOOS + "-" + architecture + ".tar.gz"
        elasticsearchFilePath := "elasticsearch-" + elasticsearchVersion + ".tar.gz"


        _, err := os.Stat(elasticsearchFilePath)
        if errors.Is(err, os.ErrNotExist) {
                err = downloadFile(elasticsearchFilePath, elasticsearchUrl)
                if err != nil {
                        panic(err)
                }

                fmt.Println("File downloaded successfully to " + elasticsearchFilePath)
        }

        elasticsearchExtractedDir := "elasticsearch-" + elasticsearchVersion
        _, err = os.Stat(elasticsearchExtractedDir)
        if errors.Is(err, os.ErrNotExist) {
                err = extractTarGzArchive(elasticsearchFilePath, ".")
                if err != nil {
                        panic(err)
                }
        }

        camundaFilePath := "camunda-zeebe-" + camundaVersion + ".tar.gz"
        camundaUrl := "https://github.com/camunda/camunda/releases/download/" + camundaVersion + "/" + camundaFilePath

        _, err = os.Stat(camundaFilePath)
        if errors.Is(err, os.ErrNotExist) {
                err = downloadFile(camundaFilePath, camundaUrl)
                if err != nil {
                        panic(err)
                }

                fmt.Println("File downloaded successfully to " + camundaFilePath)
        }
        camundaExtractedDir := "camunda-zeebe-" + camundaVersion
        _, err = os.Stat(camundaExtractedDir)
        if errors.Is(err, os.ErrNotExist) {
                err = extractTarGzArchive(camundaFilePath, ".")
                if err != nil {
                        panic(err)
                }
        }


        connectorsFilePath := "connector-runtime-bundle-" + camundaVersion + "-with-dependencies.jar"
        connectorsUrl := "https://repo1.maven.org/maven2/io/camunda/connector/connector-runtime-bundle/" + camundaVersion + "/" + connectorsFilePath

        _, err = os.Stat(connectorsFilePath)
        if errors.Is(err, os.ErrNotExist) {
                err = downloadFile(connectorsFilePath, connectorsUrl)
                if err != nil {
                        panic(err)
                }

                fmt.Println("File downloaded successfully to " + connectorsFilePath)
        }
}
