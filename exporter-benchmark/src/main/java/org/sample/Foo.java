package org.sample;

import java.util.Random;

public class Foo {

    public static void main(String[] args) {

        Random r = new Random();
        for (int i = 0; i < 100_000_000; i++) {
            r.nextInt();
        }

        System.out.println("Done!");
    }
}
