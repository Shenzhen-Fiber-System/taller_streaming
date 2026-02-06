# ☢️ Fundamentos de Project Reactor (WebFlux)
> **Tiempo Estimado:** 30 minutos
> **Objetivo:** Que los alumnos entiendan por qué su código no ejecuta línea por línea y cómo manipular el tiempo y los datos sin bloquear el hilo.

---

## 1. El Problema: Blocking vs Non-Blocking (5 min)

### 🗣️ Narrativa
"Imaginen que son dueños de una pizzería."

**Enfoque Bloqueante (Spring MVC / Servlet):**
*   Tienes 1 cocinero (Thread).
*   Llega un pedido. El cocinero mete la pizza al horno.
*   **El cocinero se queda mirando el horno 10 minutos sin hacer nada.**
*   Si llega otro pedido, necesitas *otro* cocinero (otro Thread).
*   **Problema:** Si tienes 200 pedidos, necesitas 200 cocineros. La memoria RAM explota.

**Enfoque Reactivo (WebFlux / Netty):**
*   Tienes 1 cocinero (Event Loop).
*   Mete la pizza al horno y le pega un post-it: *"Avísame cuando suene el timbre"*.
*   Inmediatamente atiende el siguiente pedido.
*   Cuando suena el timbre (Evento I/O), vuelve a sacar la pizza.
*   **Resultado:** 1 cocinero maneja 1000 pedidos simultáneos.

---

## 2. Los Tipos de Datos: Mono vs Flux (5 min)

No retornamos objetos (`User`), retornamos *promesas de futuro* (`Mono<User>`).

| Tipo | Cardinalidad | Analogía | Ejemplo Real |
|:----:|:------------:|:--------:|:------------:|
| **Mono<T>** | 0 o 1 elemento | "Una caja de Amazon que llega mañana" | `findById`, `save` |
| **Flux<T>** | 0 a N elementos | "Una cinta transportadora de sushi" | `findAll`, `Twitter Stream` |

> **Pregunta clave:** *"¿Qué pasa si tengo un `List<User>`?"*
> **Respuesta:** *"Eso es un objeto bloqueante. En Reactor preferimos `Flux<User>`, porque los usuarios pueden bajar uno por uno mientras se procesan, sin esperar a tener la lista completa en memoria."*

---

## 3. La Regla de Oro: "Nada pasa hasta que te suscribes" (5 min)

### 💻 Ejemplo en Vivo
Escribir esto en un test o main:

```java
// Esto NO HACE NADA
Mono.just("Hola Mundo")
    .map(String::toUpperCase)
    .doOnNext(System.out::println); 
```

> *"Chicos, ¿por qué no imprime nada?"*
>
> **Explicación:** *"Un Stream es como una tubería de agua. Puedes construir la tubería más compleja del mundo, con filtros y válvulas. Pero si no abres el grifo al final (**subscribe**), no sale ni una gota."*

En Spring WebFlux, **el Framework (Netty) abre el grifo** cuando llega una request HTTP. Ustedes solo construyen la tubería.

---

## 4. Operadores Vitales (10 min)

Son las herramientas para modificar los datos. Solo necesitan dominar 3 hoy:

### A. `map` (Síncrono 1-a-1)
*   **Uso:** Transformar el dato simple.
*   **Ejemplo:** `User` -> `UserDTO`.
*   **Analogía:** Abrir la caja de Amazon y envolver el producto en regalo.

### B. `flatMap` (Asíncrono 1-a-N / Async) 🚨 *EL MÁS IMPORTANTE*
*   **Uso:** Cuando necesitas llamar a *otra* cosa reactiva (DB, API externa) usando el dato que tienes.
*   **El Problema del `map`:**
    *   Si usas `map` para llamar a la DB: `Mono<Mono<User>>` (Una caja dentro de otra caja).
    *   Si usas `flatMap`: `Mono<User>` (Aplana las cajas).
*   **Regla:** *"Si tu función retorna un Mono/Flux, usa flatMap. Si retorna un objeto, usa map."*

### C. `switchIfEmpty` (El "Else")
*   **Uso:** Qué hacer si la caja viene vacía (ej: usuario no encontrado).
*   **Código:**
    ```java
    repo.findById(id)
        .switchIfEmpty(Mono.error(new NotFoundException()));
    ```

---

## 5. Errores de Novato (Common Pitfalls) (5 min)

1.  **Hacer `.block()`:**
    *   *"Jamás. Prohibido. Si hacen block() detenemos al único cocinero que tenemos. Todo el servidor se congela."*

2.  **Try-Catch tradicional:**
    *   No funciona porque el error ocurre en el futuro (en otro thread).
    *   **Solución:** Usar operadores de error: `.onErrorResume()` o `.onErrorReturn()`.

3.  **Olvidar el return:**
    *   ```java
        public Mono<User> update(User u) {
            repo.save(u); // ❌ ERROR: Se lanza al vacío y nadie lo espera
            return Mono.just(u);
        }
        ```
    *   **Corrección:**
        ```java
        public Mono<User> update(User u) {
            return repo.save(u); // ✅ Encadenar siempre
        }
        ```

---

## 🧪 Cheat Sheet para el Taller

| Quiero... | Operador |
|:---------|:---------|
| Cambiar el valor | `.map()` |
| Llamar a DB o API | `.flatMap()` |
| Ejecutar algo y seguir igual | `.doOnNext()` (Loggear) |
| Lanzar error si está vacío | `.switchIfEmpty(Mono.error())` |
| Ejecutar dos cosas a la vez | `.zip()` |
