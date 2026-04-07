package com.hmdp.utils;

import cn.hutool.core.lang.UUID;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TokenGen {
    public static void main(String[] args) throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // 生成去掉横杠的 UUID，和你图片中的格式完全一致
            tokens.add(UUID.randomUUID().toString().replace("-", ""));
        }
        Files.write(Paths.get("tokens.txt"), tokens);
        System.out.println("1000个Token已生成到 tokens.txt");
    }}
