# 🛠️ Prerrequisitos del Taller: Streaming con WebRTC y WebFlux

> **⚠️ IMPORTANTE:** Para aprovechar el taller, es VITAL que vengas con el entorno listo. No podremos detener la clase para instalar Java o configurar el IDE.

---

## 1. Hardware Mínimo Recomendado 💻
*   **RAM:** 8 GB mínimo (16 GB recomendado). Levantaremos Java + MySQL + IDE + Navegador.
*   **CPU:** i5 de 8va gen o superior / Apple M1 o superior.
*   **Espacio en Disco:** 10 GB libres.
*   **Sistema Operativo:** Windows 10/11 (WSL2 recomendado), macOS, o Linux.

---

## 2. Software Requerido (Instalar ANTES del taller) 📦

### A. Java Development Kit (JDK) 21
Necesitamos Java 21 LTS por las mejoras en Virtual Threads y Records.
*   **Verificar:** `java -version`
*   **Descargar:** [Eclipse Temurin (Adoptium)](https://adoptium.net/temurin/releases/?version=21)

### B. IDE (Entorno de Desarrollo)
Cualquiera de estos, actualizado a la última versión:
*   [IntelliJ IDEA Community/Ultimate](https://www.jetbrains.com/idea/download/) (Recomendado)
*   [VS Code](https://code.visualstudio.com/) (Instalar "Extension Pack for Java" y "Spring Boot Extension Pack")

### C. Herramientas de Línea de Comandos
*   **Git:** Para clonar el repositorio. [Descargar](https://git-scm.com/downloads)
*   **cURL:** Para probar la API. (Viene en Windows/Mac/Linux modernos).

### D. Cuentas y Accesos 🔑
Indispensable traer esto configurado desde casa:
*   **Cuenta de Git:** (GitHub, GitLab o Bitbucket).
*   **Permisos de Escritura:** Debes poder crear repositorios nuevos y pushear código (`git push`) desde la laptop que usarás en el taller.

#### 🔐 Conectarse a GitHub desde Git por Consola

##### Paso 1: Crear Cuenta en GitHub (si no tienes)

Ve a [github.com/signup](https://github.com/signup) y crea tu cuenta:
- Email
- Contraseña segura
- Nombre de usuario

##### Paso 2: Configurar Git en tu Terminal

```bash
# Verificar que Git está instalado
git --version

# Configurar tu identidad
git config --global user.name "Tu Nombre Completo"
git config --global user.email "tu-email@example.com"

# Verificar configuración
git config --global user.name
git config --global user.email
```

##### Paso 3: Clonar un Repositorio

Cuando clones un repositorio, **Git automáticamente abrirá tu navegador** pidiendo que inicies sesión en GitHub:

```bash
# Clonar cualquier repositorio
git clone https://github.com/usuario/proyecto.git
```

**¿Qué pasará?**
1. Git detecta que necesitas autenticación
2. Abre automáticamente tu navegador
3. Te pide iniciar sesión con tu **usuario y contraseña** de GitHub
4. Autorizas la conexión
5. ¡Listo! El repositorio se clona y Git guarda tus credenciales

**Siguiente vez:** Git ya no te pedirá login, usa las credenciales guardadas automáticamente.

#### ✅ Verificación para el Taller

Asegúrate de tener:

1. ✅ Cuenta en GitHub (memoriza usuario y contraseña)
2. ✅ Git instalado (`git --version`)
3. ✅ Identidad configurada:
   ```bash
   git config --global user.name "Tu Nombre"
   git config --global user.email "tu-email@example.com"
   ```
4. ✅ Haber clonado al menos un repositorio para que Git guarde tus credenciales

#### ❗ Solución de Problemas

**Git no abre el navegador:**
- Asegúrate de tener Git Credential Manager instalado (viene con Git moderno)
- Descarga Git desde [git-scm.com](https://git-scm.com)

**Borrar credenciales guardadas (Windows):**
```bash
git credential-manager erase https://github.com
```

---

## 3. Conocimientos Previos Indispensables 🧠
> **Nota:** Si no manejas estos conceptos, te será muy difícil seguir el ritmo, ya que no nos detendremos a explicarlos.
*   **Java Básico/Intermedio:** Clases, Interfaces, Lambdas.
*   **HTTP:** Entender qué es GET, POST, Headers, JSON.
*   **Spring Boot:** Nociones básicas (qué es un Bean, Inyección de Dependencias).
*   *No necesitas saber WebFlux ni WebRTC, eso lo aprenderemos aquí.*

---

## 4. 🚀 Generando el Proyecto (Spring Initializr)

Si quieres practicar desde cero o entender cómo se creó el esqueleto del proyecto, sigue estos pasos:

### Paso 1: Ir a [start.spring.io](https://start.spring.io/)

### Paso 2: Configuración del Proyecto (Project Metadata)
*   **Project:** Maven
*   **Language:** Java
*   **Spring Boot:** 3.2.x o 3.3.x (La última estable)
*   **Group:** `com.ourshop`
*   **Artifact:** `streaming`
*   **Name:** `streaming`
*   **Description:** Streaming Platform with WebRTC and WebFlux
*   **Package name:** `com.ourshop.streaming`
*   **Packaging:** Jar
*   **Java:** 21

### Paso 3: Dependencias (Dependencies) - **CRÍTICO** ⚠️
Busca y agrega (ADD DEPENDENCIES) las siguientes:

1.  **Spring Reactive Web** (No usar "Spring Web" normal)
    *   *Descripción:* Build reactive web applications with Spring WebFlux and Netty.
2.  **Spring Data R2DBC**
    *   *Descripción:* Reactive Relational Database Connectivity.
3.  **MySQL Driver**
    *   *Descripción:* JDBC and R2DBC driver for MySQL.
4.  **Lombok**
    *   *Descripción:* Java annotation library which helps to reduce boilerplate code.
5.  **Validation** (Opción I/O)
    *   *Descripción:* Bean Validation with Hibernate Validator.
6.  **Spring Boot Actuator** (Opcional)
    *   *Descripción:* Supports built-in (or custom) endpoints that let you monitor and manage your application.
7.  **Spring Boot DevTools**
    *   *Descripción:* Provides fast application restarts, LiveReload, and configurations for enhanced development experience.

### Paso 4: Generar
1.  Click en el botón **GENERATE**.
2.  Se descargará un `.zip`.
3.  Descomprimir en tu carpeta de trabajo.
4.  Abrir con tu IDE (IntelliJ: *Open...* -> Seleccionar `pom.xml` o carpeta raíz).

---

## 5. Configuración Final (Checklist) ✅

Una vez abierto el proyecto generado:

### Paso 4.5: Dependencias Manuales (Dotenv) 🛠️
La librería para leer archivos `.env` no está en el Initializr, así que debemos agregarla manualmente al `pom.xml`:

1.  Abre el archivo `pom.xml`.
2.  Dentro de la etiqueta `<dependencies>`, agrega:
    ```xml
    <!-- Dotenv para leer variables de entorno locales -->
    <dependency>
        <groupId>io.github.cdimascio</groupId>
        <artifactId>java-dotenv</artifactId>
        <version>5.2.2</version>
    </dependency>
    ```
3.  **Recarga el proyecto Maven** (Botón "M" chiquito en IntelliJ o "Refresh" en VS Code).

---

## 5. Configuración Final (Checklist) ✅

1.  **Verificar compilación:**
    Ejecuta `./mvnw clean package -DskipTests` (o `mvnw.cmd` en Windows). Debería terminar en `BUILD SUCCESS`.

2.  **Base de Datos:**
    Asegúrate de tener un servidor MySQL corriendo localmente (XAMPP, MySQL Installer, etc) y crea la base de datos:
    ```sql
    CREATE DATABASE streamingdb;
    ```

3.  **Archivo application.yml:**
    Renombra `application.properties` a `application.yml` (es más legible) y configura tu conexión R2DBC:
    ```yaml
    spring:
      r2dbc:
        url: r2dbc:mysql://localhost:3306/streamingdb
        username: root
        password: password
    ```

¡Listo! Ya tienes el entorno preparado para la **Jornada 1**. 🚀
