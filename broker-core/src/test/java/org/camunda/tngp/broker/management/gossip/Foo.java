package org.camunda.tngp.broker.management.gossip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.agrona.BitUtil;
import org.junit.Test;

public class Foo
{

    @Test
    public void test() throws IOException, NoSuchAlgorithmException
    {
        final String path = Files.createTempFile("foo-", ".raw").toFile().getAbsolutePath();
        final File file = new File(path);
        final FileOutputStream outputStream = new FileOutputStream(file);
        final MessageDigest digest = MessageDigest.getInstance("SHA1");


        final DigestOutputStream stream = new DigestOutputStream(outputStream, digest);
        final byte[] x = new byte[2];
        x[0] = (byte) 5;
        x[1] = (byte) 10;
        stream.write(x, 0, 2);
        stream.flush();
        stream.close();

//        DigestInputStream inputStream;
//        inputStream.getMessageDigest().digest()

        final byte[] encode = Base64.getEncoder().encode(stream.getMessageDigest().digest());
        System.out.println(new String(encode));

        outputStream.close();
    }

    @Test
    public void test1() throws IOException, NoSuchAlgorithmException
    {
        final byte[] input = "1234567".getBytes();

        final MessageDigest hash = MessageDigest.getInstance("SHA1");

        final ByteArrayInputStream bIn = new ByteArrayInputStream(input);
        final DigestInputStream dIn = new DigestInputStream(bIn, hash);
        final ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        int ch;
        while ((ch = dIn.read()) >= 0)
        {
            bOut.write(ch);
        }

        final byte[] newInput = bOut.toByteArray();
        System.out.println("in digest : " + new String(BitUtil.toHexByteArray(dIn.getMessageDigest().digest())));

        final DigestOutputStream dOut = new DigestOutputStream(new ByteArrayOutputStream(), hash);
        dOut.write(newInput);
        dOut.close();

        System.out.println("out digest: " + new String(BitUtil.toHexByteArray(dOut.getMessageDigest().digest())));
    }

}
