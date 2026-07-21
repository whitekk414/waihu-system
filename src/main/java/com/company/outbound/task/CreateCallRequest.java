package com.company.outbound.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateCallRequest(
    @NotBlank @Pattern(regexp = "^[0-9]{2,20}$") String extension,
    @NotBlank @Pattern(regexp = "^[a-z0-9-]{1,64}$") String promptId
) {
}
