package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogFeedMessage implements Serializable {
    private Long blogId;
    private Long userId;
    private Long timestamp;
}
