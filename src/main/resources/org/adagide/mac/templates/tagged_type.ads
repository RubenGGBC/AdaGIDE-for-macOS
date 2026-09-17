package Shapes is

   type Shape is abstract tagged private;

   function Area (Item : Shape) return Float is abstract;

   type Circle is new Shape with private;

   function Make_Circle (Radius : Float) return Circle;
   overriding function Area (Item : Circle) return Float;

private

   type Shape is abstract tagged null record;

   type Circle is new Shape with record
      Radius : Float := 0.0;
   end record;

end Shapes;
