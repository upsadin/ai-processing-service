package org.pulitko.aiprocessingservice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.dto.PromptCreateRequest;
import org.pulitko.aiprocessingservice.dto.PromptFullResponse;
import org.pulitko.aiprocessingservice.service.PromptServiceDb;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/prompts")
@Tag(name = "Админ-панель промтов", description = "Управление промтами для ИИ и JSON-схемами")
public class PromptAdminController {
    private final PromptServiceDb service;

    @Operation(summary = "Найти промт по его REF",
            description = "Например, ref 'candidate_java'")
    @GetMapping("/{ref}")
    public ResponseEntity<PromptFullResponse> get(@PathVariable String ref) {
        return ResponseEntity.ok(service.getPrompt(ref));
    }

    @Operation(summary = "Получить список всех промтов",
            description = "Возвращает все промты из базы данных со всеми данными")
    @GetMapping()
    public ResponseEntity<List<PromptFullResponse>> getAll() {
        List<PromptFullResponse> prompts = service.getAll();
        return ResponseEntity.ok(prompts);
    }

    @Operation(summary = "Изменить промт",
            description = "изменяет данные промта в соответствие с присланным")
    @PostMapping
    public Mono<ResponseEntity<PromptFullResponse>> saveOrUpdate(@RequestBody @Valid PromptCreateRequest request) {
        return Mono.fromCallable(() -> service.saveOrUpdate(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
                .doOnError(e -> log.error("Ошибка при сохранении промта: {}", e.getMessage()))
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.status(500).build());
                });
    }

    @Operation(summary = "Сменить статус (активен/неактивен)",
            description = "Позволяет быстро выключить промт, не удаляя его из базы")
    @PatchMapping("/{ref}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable String ref,
                                             @RequestParam(defaultValue = "true") boolean active) {
        service.changeStatus(ref, active);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Удалить промт",
            description = "Внимание! Это действие необратимо.")
    @DeleteMapping("/{ref}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable String ref) {
        service.delete(ref);
        return ResponseEntity.noContent().build();
    }
}
