/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org)
 * All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.extension.siddhi.execution.extrema;

import org.apache.log4j.Logger;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.siddhi.core.SiddhiAppRuntime;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.event.Event;
import org.wso2.siddhi.core.query.output.callback.QueryCallback;
import org.wso2.siddhi.core.stream.input.InputHandler;
import org.wso2.siddhi.core.stream.output.StreamCallback;
import org.wso2.siddhi.core.util.EventPrinter;
import org.wso2.siddhi.core.util.SiddhiTestHelper;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test case for bottomKLengthBatch extension.
 */
public class BottomKLengthBatchStreamProcessorExtensionTestCase {
    private static final Logger log = Logger.getLogger(
            BottomKLengthBatchStreamProcessorExtensionTestCase.class);
    private volatile int count;
    private volatile boolean eventArrived;
    private AtomicInteger eventCount;
    private int waitTime = 50;
    private int timeout = 30000;

    @BeforeMethod
    public void init() {
        count = 0;
        eventArrived = false;
        eventCount = new AtomicInteger(0);
    }

    @Test
    public void testBottomKLengthBatchStreamProcessorExtension() throws InterruptedException {
        log.info("BottomKLengthBatchStreamProcessor TestCase 1");
        SiddhiManager siddhiManager = new SiddhiManager();

        String inStreamDefinition = "define stream inputStream (item string, price long);";
        String query = ("@info(name = 'query1') " +
                "from inputStream#extrema:bottomKLengthBatch(item, 6, 3)  " +
                "insert all events into outputStream;");
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.
                createSiddhiAppRuntime(inStreamDefinition + query);

        siddhiAppRuntime.addCallback("query1", new QueryCallback() {
            @Override
            public void receive(long timeStamp, Event[] inEvents, Event[] removeEvents) {
                EventPrinter.print(timeStamp, inEvents, removeEvents);
                eventArrived = true;
                eventCount.incrementAndGet();
                if (count == 0) {
                    AssertJUnit.assertNotNull(inEvents);
                    for (Event event : inEvents) {
                        AssertJUnit.assertEquals(
                                Arrays.asList("item3", 64L, "item3", 1L, "item2", 2L, "item1", 3L),
                                Arrays.asList(event.getData())
                        );
                        AssertJUnit.assertFalse(event.isExpired());
                    }
                    AssertJUnit.assertNull(removeEvents);
                } else if (count == 1) {
                    AssertJUnit.assertNull(inEvents);
                    AssertJUnit.assertNotNull(removeEvents);
                    for (Event event : removeEvents) {
                        AssertJUnit.assertEquals(
                                Arrays.asList("item3", 64L, "item3", 1L, "item2", 2L, "item1", 3L),
                                Arrays.asList(event.getData())
                        );
                        AssertJUnit.assertTrue(event.isExpired());
                    }
                } else if (count == 2) {
                    AssertJUnit.assertNotNull(inEvents);
                    for (Event event : inEvents) {
                        AssertJUnit.assertEquals(
                                Arrays.asList("item6", 34L, "item6", 2L, "item5", 2L, "item4", 2L),
                                Arrays.asList(event.getData())
                        );
                        AssertJUnit.assertFalse(event.isExpired());
                    }
                    AssertJUnit.assertNull(removeEvents);
                }
                count++;
            }
        });

        InputHandler inputHandler = siddhiAppRuntime.getInputHandler("inputStream");
        siddhiAppRuntime.start();

        inputHandler.send(new Object[]{"item1", 10L});
        inputHandler.send(new Object[]{"item1", 13L});
        inputHandler.send(new Object[]{"item2", 65L});
        inputHandler.send(new Object[]{"item1", 74L});
        inputHandler.send(new Object[]{"item2", 25L});
        inputHandler.send(new Object[]{"item3", 64L});
        // Length Window reset
        inputHandler.send(new Object[]{"item1", 10L});
        inputHandler.send(new Object[]{"item1", 13L});
        inputHandler.send(new Object[]{"item2", 65L});
        inputHandler.send(new Object[]{"item1", 74L});
        inputHandler.send(new Object[]{"item2", 25L});
        inputHandler.send(new Object[]{"item3", 64L});
        // Length Window reset
        inputHandler.send(new Object[]{"item4", 52L});
        inputHandler.send(new Object[]{"item5", 64L});
        inputHandler.send(new Object[]{"item6", 86L});
        inputHandler.send(new Object[]{"item4", 38L});
        inputHandler.send(new Object[]{"item5", 84L});
        inputHandler.send(new Object[]{"item6", 34L});

        SiddhiTestHelper.waitForEvents(waitTime, 3, eventCount, timeout);
        AssertJUnit.assertEquals(3, count);
        AssertJUnit.assertTrue(eventArrived);
        siddhiAppRuntime.shutdown();
    }

    @Test
    public void testBottomKLengthBatchStreamProcessorExtensionWithJoin() throws InterruptedException {
        log.info("BottomKLengthBatchStreamProcessor TestCase 2");
        SiddhiManager siddhiManager = new SiddhiManager();

        String inStreamDefinition = "define stream inputStream1 (item string, price long);" +
                "define stream inputStream2 (item string, type string);";
        String query = ("@info(name = 'query1') " +
                "from inputStream1#extrema:bottomKLengthBatch(item, 6, 3) as stream1 " +
                "join inputStream2#window.lengthBatch(3)  as stream2 " +
                "on stream1.bottom1Element==stream2.item " +
                "select stream2.item as item, stream2.type as type " +
                "insert into outputStream;");
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.
                createSiddhiAppRuntime(inStreamDefinition + query);

        siddhiAppRuntime.addCallback("outputStream", new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                AssertJUnit.assertNotNull(events);
                if (events.length < 1) {
                    return;
                }
                eventArrived = true;
                AssertJUnit.assertNotNull(events);
                for (Event event : events) {
                    eventCount.incrementAndGet();
                    if (count == 0) {
                        AssertJUnit.assertEquals(
                                Arrays.asList("item3", "voucher"),
                                Arrays.asList(event.getData())
                        );
                    } else if (count == 1) {
                        AssertJUnit.assertEquals(
                                Arrays.asList("item6", "cash"),
                                Arrays.asList(event.getData())
                        );
                    }
                }
                count++;
            }
        });

        InputHandler inputHandler1 = siddhiAppRuntime.getInputHandler("inputStream1");
        InputHandler inputHandler2 = siddhiAppRuntime.getInputHandler("inputStream2");
        siddhiAppRuntime.start();

        inputHandler1.send(new Object[]{"item1", 10L});
        inputHandler1.send(new Object[]{"item1", 13L});
        inputHandler1.send(new Object[]{"item2", 65L});
        inputHandler1.send(new Object[]{"item1", 74L});
        inputHandler1.send(new Object[]{"item2", 25L});
        inputHandler1.send(new Object[]{"item3", 64L});
        inputHandler2.send(new Object[]{"item1", "cash"});
        inputHandler2.send(new Object[]{"item2", "credit card"});
        inputHandler2.send(new Object[]{"item3", "voucher"});
        // Length Window reset
        inputHandler1.send(new Object[]{"item4", 65L});
        inputHandler1.send(new Object[]{"item5", 45L});
        inputHandler1.send(new Object[]{"item6", 34L});
        inputHandler1.send(new Object[]{"item4", 76L});
        inputHandler1.send(new Object[]{"item5", 93L});
        inputHandler1.send(new Object[]{"item6", 23L});
        inputHandler2.send(new Object[]{"item5", "voucher"});
        inputHandler2.send(new Object[]{"item4", "credit card"});
        inputHandler2.send(new Object[]{"item6", "cash"});

        SiddhiTestHelper.waitForEvents(waitTime, 2, eventCount, timeout);
        AssertJUnit.assertEquals(2, count);
        AssertJUnit.assertTrue(eventArrived);
        siddhiAppRuntime.shutdown();
    }
}
