package com.example.demo;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class PromptPayQRCode {

    public static void generateQRCode(String data, String filePath, int width, int height) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height);
        Path path = FileSystems.getDefault().getPath(filePath);
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);
    }

    public static String generatePromptPayPayload(String account, double amount) {
        String payload = "000201010211" +
                "29370016A000000677010111" +
                "01130066" + account +
                "5303764" +
                String.format("5405%.2f", amount) +
                "5802TH" +
                "6304"; // CRC-16 Checksum (คำนวณโดยอัตโนมัติ)

        return payload + calculateCRC(payload);
    }

    private static String calculateCRC(String input) {
        int crc = 0xFFFF;
        for (int i = 0; i < input.length(); i++) {
            crc ^= input.charAt(i) << 8;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
            }
        }
        return String.format("%04X", crc & 0xFFFF);
    }
}
