package com.example.demo.config;

import com.linecorp.bot.client.LineMessagingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LineBotConfig {

    @Value("${line.bot.channelToken}")
    private String channelToken;

    @Bean
    public LineMessagingClient lineMessagingClient() {
        return LineMessagingClient.builder(channelToken).build();
    }
}
