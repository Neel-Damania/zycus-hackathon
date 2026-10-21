package com.zycus.hackathon.suggestion;

import jakarta.validation.constraints.Size;

/** Optional note from the reviewer; rejection notes are fed back to the AI advisor. */
public record ReviewRequest(@Size(max = 500) String note) {
}
