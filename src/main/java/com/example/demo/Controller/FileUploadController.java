package com.example.demo.Controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/uploads")
public class FileUploadController {

    private static final String UPLOAD_DIR = "uploads/";

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path path = Paths.get(UPLOAD_DIR + fileName);

            // สร้างโฟลเดอร์ถ้ายังไม่มี
            Files.createDirectories(Paths.get(UPLOAD_DIR));

            // บันทึกไฟล์
            Files.write(path, file.getBytes());
            String fileUrl = "http://localhost:8080/uploads/" + fileName;
            System.out.println("Uploading file " + fileUrl);
            return ResponseEntity.ok(fileUrl);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("❌ อัปโหลดไฟล์ล้มเหลว: " + e.getMessage());
        }
    }
}
