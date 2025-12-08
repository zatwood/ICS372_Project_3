package com.abc;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

public class FileWatcherTest {

    @TempDir
    Path tempDir;

    private TestListener listener;

    static class TestListener implements OrderIn.OrderUpdateListener{

        private final List<List<Order>> updates = new CopyOnWriteArrayList<>();

        @Override
        public void onOrdersUpdated(List<Order> newOrders){
            updates.add(newOrders);
        }

        @Override
        public void onOrdersReloaded(List<Order> allOrders){

        }

        public List<List<Order>> getUpdates(){
            return updates;
        }
    }

    @BeforeEach
    void setup(){

        listener = new TestListener();
        OrderIn.INSTANCE.addOrderUpdateListener(listener);
        OrderIn.INSTANCE.clearProcessedFiles();
    }

    @AfterEach
    void cleanUp() {
        OrderIn.INSTANCE.stopFileWatcher();
        OrderIn.INSTANCE.removeOrderUpdateListener(listener);
    }
    //helper that waits for async watcher events
    private void waitForWatcher() throws InterruptedException{
        Thread.sleep(500);
    }

    @Test
    void testFileWatcherDetectsNewJsonFile() throws Exception{

        OrderIn.INSTANCE.startFileWatcher(tempDir.toString());
        //watcher fully initialized
        Thread.sleep(100);

        String json =  """
            {
              "order": {
                "type": "Pickup",
                "order_date": 1735689600000,
                "source": "Kiosk",
                "items": [
                  { "name": "Burger", "quantity": 2, "price": 5.50 }
                ]
              }
            }
      
            """;
        Path orderFile = tempDir.resolve("order1.json");
        Files.writeString(orderFile, json);
        waitForWatcher();
        List<List<Order>> updates = listener.getUpdates();
        assertFalse(updates.isEmpty(), "Listener should've got at least 1 update");
        List<Order> firstUpdate = updates.get(0);
        assertEquals(1, firstUpdate.size(), "Should input exactly 1 order");

        Order order = firstUpdate.get(0);
        assertEquals("Pickup", order.getTypeOrDefault());
        assertEquals("Kiosk", order.getSource());
        assertEquals(1, order.getItemsOrEmpty().size());
    }

    @Test
    void testFileWatcherIgnoresDuplicateFiles() throws Exception{
        OrderIn.INSTANCE.startFileWatcher(tempDir.toString());
        Thread.sleep(100); //make sure watcher started

        String json =  """
            {
              "order": {
                "type": "Pickup",
                "order_date": 1735689600000,
                "source": "Online",
                "items": [
                  { "name": "Hot Dog", "quantity": 1, "price": 2.50 }
                ]
              }
            }
            """;

        Path orderFile = tempDir.resolve("dup.json");
        //first write
        Files.writeString(orderFile, json);
        waitForWatcher();

        int updatesAfterFirstWrite = listener.getUpdates().size();
        //modify event(second write)
        Files.writeString(orderFile, json);
        waitForWatcher();

        int updatesAfterSecondWrite = listener.getUpdates().size();
        //instead of expecting exact number, ensure that no extra updates occurred
        assertEquals(updatesAfterFirstWrite, updatesAfterSecondWrite, "Duplicate file write shouldn't trigger a second update");


    }

}
