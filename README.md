# 🏥 Mediapp Backend

Este repositorio contiene el código fuente de la API RESTful para el sistema **Mediapp**. El proyecto está construido utilizando **Java** y **Spring Boot**, siguiendo una arquitectura de capas y buenas prácticas de desarrollo backend.

## 🛠 Tecnologías Utilizadas

* **Lenguaje:** Java 17 (o 21)
* **Framework:** Spring Boot 3.x
* **Base de Datos:** PostgreSQL / MySQL (Configurable)
* **Persistencia:** Spring Data JPA / Hibernate
* **Control de Versiones:** Git & GitHub
* **Construcción:** Maven
* **Documentación API:** OpenAPI (Swagger)

---

## 🚀 Guía de Inicio Rápido

### Prerrequisitos
Asegúrate de tener instalado:
1.  JDK 17 o superior.
2.  Maven (o usar el wrapper incluido `./mvnw`).
3.  Cliente Git.

### Instalación y Ejecución

1.  **Clonar el repositorio:**
    ```bash
    git clone [https://github.com/luizhuaman/mediapp-backend.git](https://github.com/luizhuaman/mediapp-backend.git)
    cd mediapp-backend
    ```

2.  **Configurar Base de Datos:**
    Abre el archivo `src/main/resources/application.properties` y configura tus credenciales:
    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/mediapp_db
    spring.datasource.username=tu_usuario
    spring.datasource.password=tu_password
    spring.jpa.hibernate.ddl-auto=update
    ```

3.  **Ejecutar la aplicación:**
    ```bash
    ./mvnw spring-boot:run
    ```

La aplicación iniciará generalmente en: `http://localhost:8080`

---

## 📚 Documentación de la API (Swagger)

Una vez iniciada la aplicación, puedes probar los endpoints y ver la documentación interactiva en:

* **Swagger UI:** `http://localhost:8080/swagger-ui.html`
* **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

---

## 📦 Comandos de Construcción (Maven)

Para generar el artefacto desplegable (`.jar`):

```bash
# Limpiar y empaquetar sin ejecutar tests (opcional)
./mvnw clean package -DskipTests
```

## 🐙 Guía de Referencia Git (Configuración del Proyecto)
Esta sección documenta cómo se configuró este repositorio y los comandos útiles para el flujo de trabajo diario.

```bash
# 1. Inicialización local
git init

# 2. Ignorar archivos innecesarios (target, .idea, etc.)
echo "target/" > .gitignore

# 3. Vinculación con GitHub
git remote add origin [https://github.com/luizhuaman/mediapp-backend.git](https://github.com/luizhuaman/mediapp-backend.git)
git branch -M master
```

## Flujo de Trabajo Diario
### 1. Subir cambios (Push):
```bash
git status              # Ver archivos modificados
git add .               # Preparar todos los archivos
git commit -m "Mensaje" # Guardar cambios localmente
git push origin master  # Enviar a GitHub
```

### 2. Descargar cambios (Pull):
```bash
git pull origin master
```

### 3. Ver historial:
```bash
git log --oneline
```


## 🧠 Arquitectura y Prácticas de Desarrollo

Este proyecto no es solo un CRUD; implementa prácticas de desarrollo moderno enfocadas en la mantenibilidad y eficiencia:

### 🔹 Java Moderno y Programación Funcional
Se aprovechan las características de **Java 17+** para escribir código declarativo, legible y robusto:
* **Streams API:** Uso extensivo de flujos para filtrar, transformar y agregar colecciones de datos de manera eficiente, evitando bucles `for` anidados complejos.
* **Lambdas & Method References:** Sintaxis concisa para implementaciones funcionales.
* **Clase Optional:** Manejo seguro de valores nulos (Null Safety) para reducir drásticamente las `NullPointerException`.

### 🔹 Ecosistema Spring Boot
* **Inyección de Dependencias:** Desacoplamiento de componentes para facilitar el testing y la modularidad.
* **Spring Data JPA:** Abstracción de la capa de persistencia optimizando consultas a base de datos.
* **Validación Declarativa:** Uso de Bean Validation (`@Valid`, `@NotNull`) para garantizar la integridad de los datos de entrada.

### 🔹 Clean Code & Boilerplate
* **Project Lombok:** Reducción de código repetitivo (Getters, Setters, Builders) para mantener las clases de dominio limpias.
* **Patrón DTO:** Separación estricta entre las Entidades de Base de Datos y los objetos de transferencia a la API.

## ⚡ Ejemplo de Código: Enfoque Funcional

El proyecto prioriza el estilo funcional para la transformación de datos. Ejemplo de cómo se procesan las listas utilizando `Stream` y `Map`:

```java
// Ejemplo: Filtrar productos activos, aplicar descuento y obtener nombres
List<String> activeProducts = repository.findAll().stream()
    .filter(Product::isActive)                           // Predicado (Filter)
    .map(product -> applyDiscount(product, 0.10))        // Transformación (Map)
    .map(Product::getName)                               // Extracción
    .collect(Collectors.toList());                       // Reducción
```

## ⚡ Ejemplo de Implementación: Lógica Financiera Segura

Este proyecto prioriza la precisión en el manejo de datos monetarios. A continuación, un ejemplo real de cómo se utiliza **Java Streams** y **BigDecimal** para analizar transacciones, evitando la pérdida de precisión de los tipos `double`:

```java
// Caso de Uso: Filtrar créditos recientes y calcular total por canal
public Map<String, BigDecimal> analyzeRecentCredits(List<Transaction> transactions) {
    LocalDateTime cutOffDate = LocalDateTime.now().minusMonths(1);

    return transactions.stream()
        .filter(t -> t.getDate().isAfter(cutOffDate))             // 1. Filtro temporal
        .filter(t -> "CREDIT".equals(t.getType())                 // 2. Filtro de negocio
                  && t.getAmount().compareTo(new BigDecimal("1000")) > 0)
        .collect(Collectors.groupingBy(                           // 3. Agrupación
            Transaction::getChannel,
            Collectors.reducing(                                  // 4. Reducción segura
                BigDecimal.ZERO,
                Transaction::getAmount,
                BigDecimal::add
            )
        ));
}
```
<details>
<summary><b>🔍 Ver implementación: Manejo Centralizado de Errores (@ControllerAdvice)</b></summary>

El proyecto implementa `ProblemDetails` (RFC 7807) para estandarizar las respuestas de error, desacoplando la lógica de negocio del manejo de excepciones HTTP.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Error de Validación");
        
        // Mapea los errores de campo a un formato legible
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage()));
            
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }
}
```
</details>

<details>
<summary><b>🔍 Ver implementación: Excepciones</b></summary>

[Texto visible](https://eudriscabrera.com/blog/2024/manejo-de-excepciones-en-java)

En Java tenemos dos tipos de errores. Aquellos que heredan de la clase Error y los de la clase exception y así mismo ambos heredan de la clase throwable.
Una exception no es más que un error del cual podemos volver (Ej. Division ente cero), mientras que los errores terminan con el programa (Ej. Desborde de la memoria)

![Jerarquía de Excepciones](assets/diagram_errors.jpg)

Entonces dentro de las excepciones tenemos checked y unchecked exceptions.
Unchecked Exceptions: Heredan de la clase Runtime Exception y son excepciones que no necesitan ser atrapadas debido a que pueden ser prevenidas a tráves del código limpio por ejemplo comprobar que exista el indice del array (Ej. ArrayIndexOutOfBoundsException)
Checked Exceptions: Son excepciones que se detectan en tiempo de compilación por el compilador que las detecta como posible fallo y que no pueden ser prevenidas por el programador porque pueden depender de factores externos como que el usuario introduzca un numero invalido o cero (Ej. ArithmeticException).
Finalmente es una buena práctica ir de la exception más particular a la más general como se muestra a continuación:

```java
    try {
        // Code that might throw exceptions
    } catch (FileNotFoundException e) {
        // Handle specific file not found error
        System.err.println("File not found: " + e.getMessage());
    } catch (IOException e) {
        // Handle general I/O errors (parent of FileNotFoundException)
        System.err.println("I/O error: " + e.getMessage());
    } catch (Exception e) {
        // Handle any other general exception
        System.err.println("An unexpected error occurred: " + e.getMessage());
    }
```
</details>

## 👨‍💻 Sobre el Desarrollador

Este proyecto es mantenido por **Luis Huaman**, un profesional híbrido (Backend Developer & Data Engineer) apasionado por la calidad del software y la inteligencia de datos.

* **Stack Principal:** Java (Spring Boot), SQL (Oracle/Postgres), Python (PySpark).
* **Certificaciones:** Microsoft Certified: Azure Data Fundamentals (DP-900). En ruta hacia DP-600.
* **Intereses:** Inversiones bursátiles (BVL), automatización con Linux y optimización de rendimiento.
* **Filosofía de Trabajo:** Inspirado en la mejora continua (*Kaizen*) y principios de libros como *"Atomic Habits"* y *"The 5 AM Club"*.

[Visita mi LinkedIn](https://www.linkedin.com/in/luishuaman94)