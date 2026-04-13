package com.gissoft.inspection_backend.controller;
import com.gissoft.inspection_backend.dto.TimelineDto;
import com.gissoft.inspection_backend.services.CaseQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/entities")
@RequiredArgsConstructor
public class TimelineController {

    private final CaseQueryService caseQueryService;

    @GetMapping("/{entityId}/timeline")
    public List<TimelineDto> getTimeline(@PathVariable UUID entityId) {
        return caseQueryService.getTimeline(entityId);
    }
}