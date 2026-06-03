HMI y Sistema de Control para Cobot de 3 GDL
1. Introducción
Este proyecto es un sistema de Interfaz Humano-Máquina (HMI) desarrollado en Java para simular, calcular y operar un brazo robótico colaborativo (Cobot) de 3 grados de libertad (GDL).

El sistema no solo envía coordenadas, sino que calcula internamente los ángulos exactos de cada articulación utilizando cinemática inversa (algoritmos iterativos con matrices Jacobianas). Cuenta con un entorno de visualización 3D responsivo, un sistema de guardado de secuencias mediante base de datos y un emulador de hardware por red que imita el comportamiento mecánico de un microcontrolador STM32 real.

2. Requisitos del Entorno
El software está diseñado bajo estándares industriales y estructurado con Maven para asegurar la portabilidad y evitar conflictos de dependencias, especialmente en entornos Linux y gestores de ventanas dinámicos (Tiling Window Managers).

Sistema Operativo: Probado y optimizado en Arch Linux (compatible con Windows y macOS).

Lenguaje: Java Development Kit (JDK) versión 21.

Gestor de Construcción: Maven.

Librerías: JavaFX (Gráficos) y SQLite-JDBC (Bases de datos).

3. Estructura del Proyecto
Para el correcto funcionamiento de Maven, los archivos deben respetar la siguiente jerarquía:

Plaintext
Proyect/
├── pom.xml                  (Configuración de Maven)
├── robot_logs.db            (Base de datos autogenerada)
└── src/
    └── main/
        └── java/
            ├── RobotCobotUI.java    (Lógica principal e Interfaz)
            └── Stm32Emulator.java   (Servidor de emulación de hardware)
4. Instrucciones de Ejecución
El proyecto opera con dos programas corriendo en paralelo: el cerebro de la interfaz y el hardware físico (emulado). Se requieren dos terminales abiertas en la carpeta raíz del proyecto.

Terminal 1: Encender el Hardware (Emulador STM32)
Este programa actúa como el microcontrolador. Como es Java puro, se compila y ejecuta de forma nativa.

Bash
javac src/main/java/Stm32Emulator.java
java -cp src/main/java Stm32Emulator
Terminal 2: Iniciar la Interfaz HMI
Utilizando Maven, se descargarán las librerías gráficas necesarias, se compilará el código y se abrirá el panel de control.

Bash
mvn clean javafx:run
5. Explicación Técnica Detallada (Cómo Funciona)
5.1 Emulación de Hardware y Red (Sockets TCP)
El archivo Stm32Emulator.java abre un servidor local en el puerto 5000. Cuando la interfaz calcula un movimiento, manda un texto como SET_ANGLES:45.0,90.0,0.0.
Para que la simulación sea realista, el emulador hace dos cosas:

Retardo mecánico: Congela el hilo de ejecución medio segundo (Thread.sleep(500)), simulando el tiempo de viaje de los motores.

Ruido de sensores: Suma o resta pequeñas fracciones a los ángulos recibidos (ej. +0.1 grados) para emular la tolerancia mecánica o el ruido de un encoder, y devuelve esa información a la interfaz.

5.2 El Entorno 3D (JavaFX)
La simulación visual se construye desde cero sin importar modelos externos.
Se utiliza una jerarquía cinemática. En un entorno 3D, los objetos están agrupados como "padres e hijos". El Hombro es hijo de la Base, y el Codo es hijo del Hombro. Gracias a esto, si se aplica una rotación a la Base, el resto del brazo la sigue de manera natural.

Para los eslabones se usan cilindros. Como un cilindro en JavaFX rota desde su centro, se aplica un desplazamiento (setTranslateY) de la mitad de su altura. Esto mueve el punto de pivote a los extremos del cilindro, haciendo que se comporte como un hueso real articulado.

5.3 El Cerebro: Cinemática Inversa y la Matriz Jacobiana
Cuando el usuario escribe una coordenada en el espacio (X, Y, Z), el robot necesita averiguar cuántos grados debe girar cada motor para llegar ahí.

Resolver esto con despejes algebraicos es propenso a fallas (singularidades) si el brazo se estira de más. Para evitarlo, se utiliza un algoritmo iterativo llamado Descenso de Gradiente con Jacobiana Traspuesta. Funciona de forma muy intuitiva:

Medir el error: El algoritmo calcula la distancia geométrica entre la posición actual de la herramienta y la posición deseada.

Diferenciación Numérica (Prueba y error inteligente): En lugar de fórmulas complejas, el algoritmo "imagina" qué pasaría si mueve el Motor 1 un poquito (0.1 grados). Mide cómo reacciona la posición de la herramienta y anota esa tasa de cambio. Repite esto para el Motor 2 y el Motor 3.

La Matriz Jacobiana: Esos apuntes de tasas de cambio forman una matriz de 3x3. Multiplicando la forma invertida (traspuesta) de esta matriz por el vector de error actual, el algoritmo deduce en qué dirección y qué tanto debe girar cada motor para reducir el error.

Iteración: Aplica el giro (limitado a 2.0 grados máximos para mantener el control) y vuelve a medir. Este ciclo se repite miles de veces en milisegundos hasta que la herramienta llega al objetivo con un margen de error menor al configurado en la interfaz.

5.4 Base de Datos y Programación de Secuencias
El sistema cuenta con un archivo local robot_logs.db gestionado por SQLite.

La HMI funciona como un Teach Pendant industrial. El usuario puede probar coordenadas espaciales. Si una posición es útil, presiona "Grabar Punto". Estos puntos se almacenan temporalmente en la memoria RAM. Al guardar la secuencia, el sistema inserta toda la lista de coordenadas en la base de datos SQL con un nombre y un número de orden cronológico.

Al reproducir una secuencia, un hilo secundario lee la base de datos, extrae las coordenadas una por una, invoca los cálculos de la Jacobiana y manda la instrucción al robot, esperando un tiempo de seguridad entre cada paso para que el hardware termine el movimiento.

6. Diccionario de Funciones Principales
Dentro de RobotCobotUI.java, estas son las funciones que controlan el sistema:

cinematicaDirecta(t1, t2, t3): Utiliza trigonometría para calcular la posición X, Y, Z de la herramienta basándose en los ángulos actuales.

calcularCinematicaInversa(targetX, targetY, targetZ): Ejecuta el ciclo iterativo de la matriz Jacobiana para deducir los ángulos a partir de una coordenada espacial.

calcularYEnviar(x, y, z): Ejecuta la animación gráfica fluida (Timeline de JavaFX) y manda la instrucción de texto al Socket del STM32.

procesarFeedback(data): Lee la respuesta con "ruido" del emulador y anima el modelo 3D secundario (color verde) para visualizar el comportamiento real del hardware.

reproducirSecuencia(nombre): Conecta con SQLite, lee los puntos de una ruta programada y ejecuta los movimientos secuencialmente en un hilo independiente para no congelar la interfaz gráfica.
