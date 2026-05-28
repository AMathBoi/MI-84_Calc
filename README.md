TODO

1 widget
Make the widget with position x y and dimensions width height
Declare a texture location
Draw text for expression on top of texture
Make it draggable by overriding mouseClicked mouseReleased and mouseDragged to update x y
Add buttons on the widget

2 display from expression
Character sprites for each number or expression
eg in the expression "1 + 2" 1, +, and 2 will each have different sprites

3 adding the widget to the inventory
create a calculator widget object
add it to getRenderables screen and add the widget
add internal hitboxes for buttons

4 calculator logic
make it work

other important stuff
renderables are drawn in insertion order so it should be drawn last
account for screen resizing, make sure calc is set to center of screen
add some sort of user config where data is stored(eg postion and history)