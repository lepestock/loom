/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @summary Test ThredMXBean.findDeadlockedThreads with deadlocked virtual threads
 * @compile --enable-preview -source ${jdk.version} VirtualThreadDeadlocks.java
 * @run main/othervm --enable-preview VirtualThreadDeadlocks PP
 * @run main/othervm --enable-preview VirtualThreadDeadlocks PV
 * @run main/othervm --enable-preview VirtualThreadDeadlocks VV
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.stream.Stream;

public class VirtualThreadDeadlocks {
    private static final Object LOCK1 = new Object();
    private static final Object LOCK2 = new Object();

    /**
     * PP = test deadlock with two platform threads
     * PV = test deadlock with one platform thread and one virtual thread
     * VV = test deadlock with two virtual threads
     */
    public static void main(String[] args) throws Exception {

        // start thread1
        Thread.Builder builder1 = (args[0].charAt(0) == 'P')
                ? Thread.ofPlatform().daemon()
                : Thread.ofVirtual();
        Thread thread1 = builder1.start(() -> {
            synchronized (LOCK1) {
                try { Thread.sleep(1000); } catch (Exception e) { }
                synchronized (LOCK2) { }
            }
        });
        System.out.println("thread1 => " + thread1);

        // start thread2
        Thread.Builder builder2 = (args[0].charAt(1) == 'P')
                ? Thread.ofPlatform().daemon()
                : Thread.ofVirtual();
        Thread thread2 = builder2.start(() -> {
            synchronized (LOCK2) {
                try { Thread.sleep(1000); } catch (Exception e) { }
                synchronized (LOCK1) { }
            }
        });
        System.out.println("thread2 => " + thread2);

        System.out.println("Waiting for thread1 and thread2 to deadlock ...");
        Thread.sleep(2000);

        ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        long[] deadlockedThreads = bean.findDeadlockedThreads();
        if (deadlockedThreads != null)
            Arrays.sort(deadlockedThreads);
        System.out.println("findDeadlockedThreads => " + Arrays.toString(deadlockedThreads));

        long[] expectedThreads = platformThreadsToIds(thread1, thread2);
        System.out.println("expected => " + Arrays.toString(expectedThreads));

        if (!Arrays.equals(deadlockedThreads, expectedThreads))
            throw new RuntimeException("Unexpected result");
    }

    /**
     * Return an array of the thread identifiers of the platform threads in the
     * given array. Returns null if there are no platform threads.
     */
    static long[] platformThreadsToIds(Thread... threads) {
        long[] tids = Stream.of(threads)
                .filter(t -> !t.isVirtual())
                .mapToLong(Thread::threadId)
                .toArray();
        if (tids.length == 0) {
            return null;
        } else {
            Arrays.sort(tids);
            return tids;
        }
    }
}