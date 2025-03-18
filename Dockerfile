# ใช้ OpenJDK 17 หรือ 21
FROM openjdk:17-jdk-slim

# ตั้งค่าพอร์ตที่ต้องการให้ Spring Boot ใช้
EXPOSE 8081

# กำหนด working directory ใน container
WORKDIR /app

# คัดลอกไฟล์ JAR จาก build ลงไป
COPY build/libs/*.jar app.jar

# คำสั่งสำหรับรัน Spring Boot
CMD ["java", "-jar", "app.jar"]
