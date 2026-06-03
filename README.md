# HMI y Sistema de Control para Cobot de 3 GDL

## 1. Introducción
Este proyecto de Ingeniería en Automatización consiste en un sistema de Interfaz Humano-Máquina (HMI) desarrollado en Java para simular, calcular y operar un brazo robótico colaborativo (Cobot) de 3 grados de libertad (GDL). 

El sistema no solo envía coordenadas, sino que calcula internamente los ángulos exactos de cada articulación utilizando **cinemática inversa** (algoritmos iterativos con matrices Jacobianas). Cuenta con un entorno de visualización 3D responsivo, un sistema de guardado de secuencias mediante base de datos y un emulador de hardware por red que imita el comportamiento mecánico de un microcontrolador STM32 real.

---

## 2. Requisitos del Entorno
El software está estructurado con Maven para asegurar la portabilidad y evitar conflictos de dependencias, garantizando que corra perfectamente en sistemas Linux (especialmente en gestores de ventanas dinámicos).

* **Sistema Operativo:** Probado en Arch Linux (compatible con Windows y macOS).
* **Lenguaje:** Java Development Kit (JDK) versión 21.
* **Gestor de Construcción:** Maven.
* **Librerías:** JavaFX (Gráficos y 3D) y SQLite-JDBC (Bases de datos).

---

## 3. Estructura del Proyecto
Para el correcto funcionamiento de Maven, los archivos deben respetar la siguiente jerarquía en el repositorio:

```text
Proyect/
├── pom.xml                  (Configuración de Maven y dependencias)
├── robot_logs.db            (Base de datos autogenerada)
└── src/
    └── main/
        └── java/
            ├── RobotCobotUI.java    (Lógica principal e Interfaz HMI)
            └── Stm32Emulator.java   (Servidor de emulación de hardware)
```

---

## 4. Instrucciones de Ejecución
El proyecto opera con dos programas corriendo en paralelo: el hardware físico (emulado) y el cerebro de la interfaz. Se requieren dos terminales abiertas en la carpeta raíz del proyecto.

**Terminal 1: Encender el Hardware (Emulador STM32)** Este programa actúa como el microcontrolador. Al ser Java puro, se compila y ejecuta de forma nativa.
```bash
javac src/main/java/Stm32Emulator.java
java -cp src/main/java Stm32Emulator
```

**Terminal 2: Iniciar la Interfaz HMI** Utilizando Maven, se descargarán las librerías gráficas necesarias, se compilará el código y se abrirá el panel de control industrial.
```bash
mvn clean javafx:run
```

---

## 5. Arquitectura del Sistema (Cómo Funciona)

### 5.1 Emulación de Hardware y Red (Sockets TCP)
El archivo `Stm32Emulator.java` abre un servidor local en el puerto 5000. Cuando la interfaz calcula un movimiento, manda un texto como `SET_ANGLES:45.0,90.0,0.0`. Para que la simulación sea realista, el emulador ejecuta dos acciones:
1. **Retardo mecánico:** Congela el hilo de ejecución medio segundo (`Thread.sleep(500)`), simulando la inercia y el tiempo de viaje de los motores físicos.
2. **Ruido de sensores:** Suma o resta pequeñas fracciones a los ángulos recibidos (ej. +0.1 grados) para emular la tolerancia mecánica y el ruido de un encoder, devolviendo esa información a la interfaz para su validación.

### 5.2 El Entorno 3D (JavaFX)
La simulación visual se construye mediante primitivas y una **jerarquía cinemática**. En el espacio 3D, los objetos están agrupados como "padres e hijos" (`Group`). El Hombro es hijo de la Base, y el Codo es hijo del Hombro. Si se aplica una rotación a la Base, el resto de la cadena cinemática la sigue de manera natural.

Para que los eslabones cilíndricos roten desde sus extremos (como un hueso) y no desde su centro de masa, se aplica un desplazamiento (`setTranslateY`) equivalente a la mitad de su altura, modificando su punto de pivote.

### 5.3 El Cerebro: Cinemática Inversa y Matriz Jacobiana
Para traducir una coordenada espacial (X, Y, Z) a los grados que debe girar cada motor, se utiliza un algoritmo iterativo llamado **Descenso de Gradiente con Jacobiana Traspuesta**. Esto evita las singularidades matemáticas de los despejes algebraicos si el robot se estira por completo.

1. **Cálculo del error:** El algoritmo mide la distancia geométrica entre la posición actual de la herramienta y la posición deseada.
2. **Diferenciación Numérica:** El algoritmo realiza un micro-movimiento imaginario (0.1 grados) en el Motor 1. Mide cómo reacciona la posición de la herramienta y anota la tasa de cambio. Repite esto para los Motores 2 y 3.
3. **La Matriz Jacobiana:** Las tasas de cambio forman una matriz de 3x3. Multiplicando la traspuesta de esta matriz por el vector de error actual, se deduce en qué dirección y qué tanto debe girar cada motor para reducir la brecha.
4. **Iteración:** Se aplica el giro (normalizado a un máximo de 2.0 grados por ciclo para evitar inestabilidad) y se vuelve a medir. Este ciclo se repite miles de veces en milisegundos hasta converger dentro de la tolerancia configurada.

### 5.4 Base de Datos y Programación de Secuencias (Teach Pendant)
El sistema cuenta con el archivo local `robot_logs.db` gestionado por SQLite.

La HMI permite probar coordenadas espaciales. Si una posición es útil, se graba en la memoria RAM. Al guardar la secuencia, el sistema inserta toda la lista de coordenadas en la base de datos SQL con un nombre y un orden cronológico.

Al reproducir una secuencia, un hilo secundario lee la base de datos, extrae las coordenadas una por una, invoca los cálculos de la Jacobiana y manda la instrucción al robot, aplicando un retraso de seguridad (`Thread.sleep`) entre cada paso para asegurar que el hardware termine el movimiento antes de proceder.
