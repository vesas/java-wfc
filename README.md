# Simple WFC

Simple wave function collapse test project implemented in Java. Creates output texture from simple tiles based on constraints defined in code, rather than the usual image based input.

Implemented: 
* Rotations in 2d
* Output texture tileability
* Backtracking

TODO:

* 3D
* Symmetries
* ...

## How to Run

```
./gradlew desktop:run
```

## Controls

| Key     | Action                    |
|---------|---------------------------|
| `Space` | Step one round (observe + propagate) |
| `1`     | Run to completion         |
| `o`     | Observe step only         |
| `p`     | Propagate step only       |
| `g`     | Print grid to console     |
| `c`     | Print constraints         |
| `r`     | Print rotations           |

## Test output:

### Circuits
Input tiles:

![input 1](assets/circ1.png) ![input 2](assets/circ2.png) ![input 3](assets/circ3.png) ![input 4](assets/circ4.png) ![input 5](assets/circ5.png) ![input 6](assets/circ6.png) ![input 7](assets/circ7.png) ![input 8](assets/circ8.png)

Output:

![example image](wfc9.gif)

### Test:
Input tiles:

![input 1](assets/test1.png) ![input 2](assets/test2.png) ![input 3](assets/test3.png) ![input 4](assets/test4.png) ![input 5](assets/test5.png) ![input 6](assets/test6.png) ![input 7](assets/test7.png) ![input 8](assets/test8.png) ![input 9](assets/test9.png) ![input 10](assets/test10.png) ![input 11](assets/test11.png) ![input 12](assets/test12.png)

Output:

![example image](image.png)


### Cross:
Input tiles:

![input 1](assets/cross1.png) ![input 2](assets/cross2.png) ![input 3](assets/cross3.png) ![input 4](assets/cross4.png)

Output:

![example image](image2.png)

### Some fun:

![example image](wfc7.gif)

## Dependencies

Displaying is done using LibGDX