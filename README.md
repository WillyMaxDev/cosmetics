# NetsuCosmetics

Plugin de cosméticos personalizados para servidores Minecraft Paper 1.21+

## Características

- **Camino Soleado**: Bloques temporales que aparecen bajo los pies del jugador al caminar, desapareciendo automáticamente 3 segundos después de que dejas de pisar la zona
- **Aura Veraniega**: Efecto visual de agua y partículas que aparece cuando el jugador está quieto, con partículas de agua mejoradas y más burbujas
- **Brisa Marina**: Cosmético con efectos de agua y aire
- **Ground Land**: Efecto de aterrizaje personalizado

## Instalación

1. Descarga el archivo `NetsuCosmetics.jar` desde [Releases](https://github.com/WillyMaxDev/cosmetics/releases)
2. Colócalo en la carpeta `plugins` de tu servidor
3. Reinicia o recarga el servidor
4. Configura los cosméticos en los archivos de configuración generados

## Requisitos

- Paper 1.21+
- WorldGuard (opcional, para protección de zonas)

## Comandos

- `/nc` - Comando principal del plugin

## Configuración

Los cosméticos se pueden configurar editando los archivos YAML en la carpeta `plugins/NetsuCosmetics/`

## Versión

v0.1.8

## Cambios Recientes

- ✅ Arreglado ConcurrentModificationException
- ✅ Bloques temporales con desaparición automática (3 segundos)
- ✅ Removido efecto AFK del Camino Soleado
- ✅ Mejorado Aura Veraniega con más partículas
