generic
   type Element is private;
   Capacity : Positive;
package Sample_Stack is

   type Stack is limited private;

   Overflow  : exception;
   Underflow : exception;

   procedure Push (Container : in out Stack; Item : Element);
   procedure Pop  (Container : in out Stack; Item : out Element);
   function  Size (Container : Stack) return Natural;

private

   type Element_Array is array (1 .. Capacity) of Element;

   type Stack is limited record
      Items : Element_Array;
      Top   : Natural := 0;
   end record;

end Sample_Stack;
