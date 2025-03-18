package com.example.demo.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.message.TextMessage;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequiredArgsConstructor
public class LineBotController {

    private final LineMessagingClient lineMessagingClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger orderCounter = new AtomicInteger(1);
    private static final String SLIPOK_API_URL = "https://api.slipok.com/api/line/apikey/41062";
    private static final String SLIPOK_API_KEY = "SLIPOKKGWEZG0";

    // Map เก็บ Order ID และยอดเงินที่ต้องโอน
    private final Map<String, Double> orderAmountMap = new ConcurrentHashMap<>();

    @PostMapping("/callback")
    public String callback(@RequestBody String payload) {
        System.out.println("\uD83D\uDCE9 Received Payload: " + payload);

        try {
            JsonNode eventsNode = objectMapper.readTree(payload).path("events");

            if (eventsNode.isArray() && eventsNode.size() > 0) {
                for (JsonNode event : eventsNode) {
                    String replyToken = event.path("replyToken").asText();
                    String messageType = event.path("message").path("type").asText();

                    if ("text".equals(messageType)) {
                        String messageText = event.path("message").path("text").asText();
                        System.out.println("✅ Received Message: " + messageText);
                        String replyMessage = processMessage(messageText);
                        if (!replyMessage.isEmpty()) {
                            reply(replyToken, replyMessage);
                        }
                    } else if ("image".equals(messageType)) {
                        String messageId = event.path("message").path("id").asText();
                        System.out.println("🖼️ Received Image Message ID: " + messageId);
                        handleImageMessage(replyToken, messageId);
                    }
                }
            } else {
                System.out.println("⚠️ ไม่มี Event ใน Payload");
            }

        } catch (Exception e) {
            System.out.println("❌ Error parsing payload: " + e.getMessage());
            e.printStackTrace();
        }

        return "OK";
    }

    private String processMessage(String messageText) {
        String[] lines = messageText.split("\n");

        if (lines.length < 3) {
            return "❌ รูปแบบข้อความไม่ถูกต้อง กรุณาส่ง 3 บรรทัด";
        }

        String type = lines[0].trim();
        String[] numbers = lines[1].trim().split(",");
        int amount;
        try {
            amount = Integer.parseInt(lines[2].trim());
        } catch (NumberFormatException e) {
            return "❌ จำนวนเงินไม่ถูกต้อง กรุณาระบุเป็นตัวเลข";
        }

        double rate = switch (type) {
            case "3 ตัวล่าง" -> 450;
            case "2 ตัวล่าง" -> 90;
            case "3 ตัวโต๊ด" -> 100;
            default -> -1;
        };

        if (rate == -1) {
            return "❌ ประเภทตัวเลขไม่ถูกต้อง กรุณาเลือกจาก: 3 ตัวล่าง, 2 ตัวล่าง, 3 ตัวโต๊ด";
        }

        double totalAmount = numbers.length * amount;
        double totalPrice = numbers.length * amount * rate;
        String orderNumber = String.format("#%03d", orderCounter.getAndIncrement());

        orderAmountMap.put(orderNumber, totalAmount);

        return String.format(
                "✅ ยืนยันรายการ\nหมายเลข: %s\nยอดที่ต้องจ่าย: %.2f บาท\nโอนเงิน: %.2f บาท (0972311021 พร้อมเพย์)\nOrder = %s",
                String.join(", ", numbers), totalPrice, totalAmount, orderNumber
        );
    }


    private void handleImageMessage(String replyToken, String messageId) {
        try {
            String downloadUrl = "https://api-data.line.me/v2/bot/message/" + messageId + "/content";
            HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
            connection.setRequestProperty("Authorization", "Bearer " + "r4Dqv71kvlILzZLGp+NfwrjYyFjzcdkMogO9LGp077dwPnEJqQ5T1vyT412CsM93Fxn+vTHiU3x8v3dlfFCAlROyPedklTqtjde0pXS0uSI5gBLCN0pfSMXUK+7XX5BdbjdiUltuGXhVFOhmdKQ/PAdB04t89/1O/w1cDnyilFU=");

            File file = new File("temp_image.jpg");
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            String resultMessage = verifySlip(file);
            reply(replyToken, resultMessage);
            file.delete();

        } catch (Exception e) {
            e.printStackTrace();
            reply(replyToken, "❌ ไม่สามารถตรวจสอบสลิปได้: " + e.getMessage());
        }
    }

    // Map เก็บ transactionId ของสลิปที่เคยใช้ไปแล้ว
    private final Map<String, Boolean> usedTransactions = new ConcurrentHashMap<>();

    private String verifySlip(File file) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(SLIPOK_API_URL);
            post.setHeader("x-authorization", SLIPOK_API_KEY);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("files", file);

            HttpEntity multipart = builder.build();
            post.setEntity(multipart);

            try (CloseableHttpResponse response = client.execute(post)) {
                String responseString = EntityUtils.toString(response.getEntity());

                // ✅ พิมพ์ค่าที่ API ส่งกลับมาเพื่อตรวจสอบ
                System.out.println("📩 API Raw Response: " + responseString);

                JsonNode jsonNode = objectMapper.readTree(responseString);
                boolean success = jsonNode.path("data").path("success").asBoolean();
                double amount = jsonNode.path("data").path("amount").asDouble();
                String transactionId = jsonNode.path("data").path("transRef").asText();  // ✅ เพิ่มการอ่าน `transRef`

                // ✅ Log ค่าที่อ่านได้จาก JSON
                System.out.println("✅ อ่านค่าจาก API -> Success: " + success + " | Amount: " + amount + " | Transaction ID: " + transactionId);

                // ✅ ตรวจสอบว่าสลิปนี้ถูกใช้ไปแล้วหรือยัง
                if (usedTransactions.containsKey(transactionId)) {
                    return "❌ สลิปนี้ถูกใช้ไปแล้ว กรุณาส่งสลิปใหม่";
                }

                // ตรวจสอบยอดเงินกับ Order ID ที่ส่งมาก่อนหน้า
                for (Map.Entry<String, Double> entry : orderAmountMap.entrySet()) {
                    double expectedAmount = entry.getValue();
                    System.out.println("🔍 ตรวจสอบ Order " + entry.getKey() + " -> คาดว่า: " + expectedAmount + " บาท | ได้รับจาก API: " + amount);

                    if (Math.abs(expectedAmount - amount) < 0.01) { // ✅ ตรวจสอบ floating-point error
                        // ✅ บันทึกว่า Transaction นี้ใช้ไปแล้ว
                        usedTransactions.put(transactionId, true);
                        return "✅ โอนเงินสำเร็จสำหรับ Order " + entry.getKey() + "\nยอดที่โอน: " + amount + " บาท";
                    }
                }

                return "❌ ยอดเงินไม่ถูกต้อง กรุณาตรวจสอบอีกครั้ง\nยอดที่โอน: " + amount + " บาท";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ เกิดข้อผิดพลาดในการตรวจสอบสลิป: " + e.getMessage();
        }
    }




    private void reply(String replyToken, String messageText) {
        try {
            lineMessagingClient.replyMessage(new ReplyMessage(replyToken, new TextMessage(messageText))).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
