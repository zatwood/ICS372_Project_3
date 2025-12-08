package com.abc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderInTest {

    @BeforeEach
    void resetProcessedFiles() {
        OrderIn.INSTANCE.clearProcessedFiles();
    }
    @Test
    void testReadValidJsonOrder() throws Exception{
        Path tempDir = Files.createTempDirectory("orderTest");

        String json =  """
            {
              "order": {
                "type": "Pickup",
                "order_date": 1735689600000,
                "source": "App",
                "items": [
                  { "name": "Burger", "quantity": 2, "price": 10.50 }
                ]
              }
            }
        """;

        Files.writeString(tempDir.resolve("order1.json"), json);

        List<Order> orders = OrderIn.INSTANCE.readOrdersFromDirectory(tempDir.toString());
        assertEquals(1, orders.size());

        Order order = orders.get(0);
        assertEquals("Pickup", order.getTypeOrDefault());
        assertEquals("App", order.getSource());
        assertEquals(1735689600000L, order.getOrder_date());
        assertEquals(2, order.getItems().get(0).getQuantity());

    }

    @Test
    void testLoadMultipleFiles() throws Exception {
        Path tempDir = Files.createTempDirectory("orderTestMulti");

        Files.writeString(tempDir.resolve("1.json"),
                """
                {
                  "order": {
                    "type": "A",
                    "order_date": 1,
                    "items": [
                      { "name": "Item1", "quantity": 1, "price": 1.0 }
                    ]
                  }
                }
                """
        );

        Files.writeString(tempDir.resolve("2.json"),
                """
                {
                  "order": {
                    "type": "B",
                    "order_date": 2,
                    "items": [
                      { "name": "Item2", "quantity": 1, "price": 2.0 }
                    ]
                  }
                }
                """
        );

        List<Order> orders = OrderIn.INSTANCE.readOrdersFromDirectory(tempDir.toString());
        assertEquals(2, orders.size());
    }

}
