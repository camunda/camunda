/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
import com.ctc.wstx.util.StringUtil;
import org.junit.Test;
import com.azure.storage.file.share.*;

public final class PrototypeTest {
  public static final String connectStr =
      "DefaultEndpointsProtocol=https;"
          + "AccountName=rltest123;"
          + "AccountKey=BxA/w5K8tpFjjsXYCiH+mZBQVjDInt9T6USNUwSEV+VuEnWgy3BLoNQZocer5v4Z1eMuL+PWqRmG+AStHFJorQ==;"
          + "EndpointSuffix=core.windows.net";
  @Test
  public void startTest() {
    String shareName = "test1";

//    ShareClient shareClient = new ShareClientBuilder()
//        .connectionString(connectStr).shareName(shareName)
//        .buildClient();

    createFileShare(connectStr, shareName);
  }

  public static Boolean createFileShare(String connectStr, String shareName)
  {
    try
    {
      ShareClient shareClient = new ShareClientBuilder()
          .connectionString(connectStr).shareName(shareName)
          .buildClient();

      shareClient.create();
      return true;
    }
    catch (Exception e)
    {
      System.out.println("createFileShare exception: " + e.getMessage());
      return false;
    }
  }

  public static Boolean deleteFileShare(String connectStr, String shareName)
  {
    try
    {
      ShareClient shareClient = new ShareClientBuilder()
          .connectionString(connectStr).shareName(shareName)
          .buildClient();

      shareClient.delete();
      return true;
    }
    catch (Exception e)
    {
      System.out.println("deleteFileShare exception: " + e.getMessage());
      return false;
    }
  }

  public static Boolean createDirectory(String connectStr, String shareName,
      String dirName)
  {
    try
    {
      ShareDirectoryClient dirClient = new ShareFileClientBuilder()
          .connectionString(connectStr).shareName(shareName)
          .resourcePath(dirName)
          .buildDirectoryClient();

      dirClient.create();
      return true;
    }
    catch (Exception e)
    {
      System.out.println("createDirectory exception: " + e.getMessage());
      return false;
    }
  }

  public static Boolean deleteDirectory(String connectStr, String shareName,
      String dirName)
  {
    try
    {
      ShareDirectoryClient dirClient = new ShareFileClientBuilder()
          .connectionString(connectStr).shareName(shareName)
          .resourcePath(dirName)
          .buildDirectoryClient();

      dirClient.delete();
      return true;
    }
    catch (Exception e)
    {
      System.out.println("deleteDirectory exception: " + e.getMessage());
      return false;
    }
  }

  public static Boolean enumerateFilesAndDirs(String connectStr, String shareName,
      String dirName)
  {
    try
    {
      ShareDirectoryClient dirClient = new ShareFileClientBuilder()
          .connectionString(connectStr).shareName(shareName)
          .resourcePath(dirName)
          .buildDirectoryClient();

      dirClient.listFilesAndDirectories().forEach(
          fileRef -> System.out.printf("Resource: %s\t Directory? %b\n",
              fileRef.getName(), fileRef.isDirectory())
      );

      return true;
    }
    catch (Exception e)
    {
      System.out.println("enumerateFilesAndDirs exception: " + e.getMessage());
      return false;
    }
  }

  public static Boolean uploadFile(String connectStr, String shareName,
      String dirName, String fileName)
  {
    try
    {
      ShareDirectoryClient dirClient = new ShareFileClientBuilder()
          .connectionString(connectStr).shareName(shareName)
          .resourcePath(dirName)
          .buildDirectoryClient();

      ShareFileClient fileClient = dirClient.getFileClient(fileName);
      fileClient.create(1024);
      fileClient.uploadFromFile(fileName);
      return true;
    }
    catch (Exception e)
    {
      System.out.println("uploadFile exception: " + e.getMessage());
      return false;
    }
  }

  public static Boolean downloadFile(String connectStr, String shareName,
      String dirName, String destDir,
      String fileName)
  {
    try
    {
      ShareDirectoryClient dirClient = new ShareFileClientBuilder()
          .connectionString(connectStr).shareName(shareName)
          .resourcePath(dirName)
          .buildDirectoryClient();

      ShareFileClient fileClient = dirClient.getFileClient(fileName);

      // Create a unique file name
      String date = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
      String destPath = destDir + "/"+ date + "_" + fileName;

      fileClient.downloadToFile(destPath);
      return true;
    }
    catch (Exception e)
    {
      System.out.println("downloadFile exception: " + e.getMessage());
      return false;
    }
  }

  public static Boolean deleteFile(String connectStr, String shareName,
      String dirName, String fileName)
  {
    try
    {
      ShareDirectoryClient dirClient = new ShareFileClientBuilder()
          .connectionString(connectStr).shareName(shareName)
          .resourcePath(dirName)
          .buildDirectoryClient();

      ShareFileClient fileClient = dirClient.getFileClient(fileName);
      fileClient.delete();
      return true;
    }
    catch (Exception e)
    {
      System.out.println("deleteFile exception: " + e.getMessage());
      return false;
    }
  }
}
